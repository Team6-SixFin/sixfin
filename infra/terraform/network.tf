# Phase 3 — 네트워크 계층
# 서브넷 분리 기준: 상태를 가지는가. PG/Redis/RabbitMQ는 프라이빗, 무상태 앱과
# 사용자 접점(Gateway·Zipkin UI)은 퍼블릭.

data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "${var.project_prefix}-vpc" }
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = "${var.aws_region}a"
  map_public_ip_on_launch = true

  tags = { Name = "${var.project_prefix}-public" }
}

resource "aws_subnet" "private" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.2.0/24"
  availability_zone = "${var.aws_region}a"

  tags = { Name = "${var.project_prefix}-private" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "${var.project_prefix}-igw" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${var.project_prefix}-public-rt" }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# ── 보안 그룹 ────────────────────────────────────────────────────────────

resource "aws_security_group" "nat" {
  name_prefix = "${var.project_prefix}-nat-"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "VPC internal - outbound NAT traffic from private subnet"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["10.0.0.0/16"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_prefix}-nat-sg" }
}

resource "aws_security_group" "app" {
  name_prefix = "${var.project_prefix}-app-"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.my_ip]
  }

  ingress {
    description = "Gateway - external entrypoint"
    from_port   = 19091
    to_port     = 19091
    protocol    = "tcp"
    cidr_blocks = [var.my_ip]
  }

  ingress {
    description = "Zipkin UI"
    from_port   = 9411
    to_port     = 9411
    protocol    = "tcp"
    cidr_blocks = [var.my_ip]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_prefix}-app-sg" }
}

resource "aws_security_group" "data" {
  name_prefix = "${var.project_prefix}-data-"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL - from app-sg only"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  ingress {
    description     = "Redis - from app-sg only"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  ingress {
    description     = "RabbitMQ - from app-sg only"
    from_port       = 5672
    to_port         = 5672
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  ingress {
    description     = "RabbitMQ management UI - from app-sg only, access via SSH tunnel"
    from_port       = 15672
    to_port         = 15672
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  ingress {
    description     = "SSH - via App EC2 as bastion"
    from_port       = 22
    to_port         = 22
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  # Kafka 추가
  ingress {
    description     = "Kafka plaintext/clients - from app-sg only"
    from_port       = 9092
    to_port         = 9092
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  ingress {
    description     = "Kafka controller/internal - from app-sg only"
    from_port       = 9093
    to_port         = 9093
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_prefix}-data-sg" }
}

# ── NAT Instance (NAT Gateway 대비 비용 절감) ───────────────────────────

resource "aws_instance" "nat" {
  ami                    = data.aws_ssm_parameter.al2023_ami.value
  instance_type          = "t3.micro"
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.nat.id]
  key_name               = aws_key_pair.main.key_name

  # NAT 역할을 하려면 자신을 향하지 않는 트래픽도 전달해야 하므로 비활성화 필수.
  # 빠뜨리면 NAT가 조용히 동작하지 않는다.
  source_dest_check = false

  # Amazon Linux 2023엔 iptables가 기본 설치돼 있지 않다(nftables 네이티브).
  # 명령어가 없어 MASQUERADE 규칙이 조용히 실패했던 적이 있다.
  # iptables-services는 /etc/sysconfig/iptables를 부팅 시 자동 복원하는
  # systemd 유닛(iptables.service)을 제공한다 — user_data 재적용이 인스턴스
  # stop/start를 유발해 규칙이 한 번 날아간 적이 있어(이 값을 다시 바꾸면 또
  # 재부팅된다) 재부팅 복원까지 넣어뒀다.
  user_data = <<-EOF
    #!/bin/bash
    set -eux
    dnf install -y iptables-services
    sysctl -w net.ipv4.ip_forward=1
    echo "net.ipv4.ip_forward = 1" >> /etc/sysctl.conf
    IFACE=$(ip -o -4 route show to default | awk '{print $5}')
    iptables -t nat -A POSTROUTING -o "$IFACE" -j MASQUERADE
    mkdir -p /etc/sysconfig
    iptables-save > /etc/sysconfig/iptables
    systemctl enable iptables
  EOF

  tags = { Name = "${var.project_prefix}-nat" }
}

# ── 프라이빗 라우팅 (NAT Instance의 ENI로) ──────────────────────────────

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block           = "0.0.0.0/0"
    network_interface_id = aws_instance.nat.primary_network_interface_id
  }

  tags = { Name = "${var.project_prefix}-private-rt" }
}

resource "aws_route_table_association" "private" {
  subnet_id      = aws_subnet.private.id
  route_table_id = aws_route_table.private.id
}

# ── SSH 키페어 (공개키만 다룬다) ─────────────────────────────────────────

resource "aws_key_pair" "main" {
  key_name   = "${var.project_prefix}-key"
  public_key = var.ssh_public_key
}
