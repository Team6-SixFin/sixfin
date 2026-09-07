# ── Phase 4 — 컴퓨트 계층 ────────────────────────────────────────────────

data "aws_iam_policy_document" "ec2_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "app" {
  name               = "${var.project_prefix}-app-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
}

# App EC2가 ECR에서 이미지를 pull하는 용도
resource "aws_iam_role_policy_attachment" "app_ecr_readonly" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# GitHub Actions가 SSM Send-Command로 배포하기 위한 SSM 정책
resource "aws_iam_role_policy_attachment" "app_ssm" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "app" {
  name = "${var.project_prefix}-app-profile"
  role = aws_iam_role.app.name
}

# Docker + compose plugin 설치, 2GB swap 생성 (양쪽 EC2 공통)
locals {
  bootstrap_user_data = <<-EOF
    #!/bin/bash
    set -eux

    dnf install -y docker
    systemctl enable --now docker
    usermod -aG docker ec2-user

    mkdir -p /usr/local/lib/docker/cli-plugins
    curl -fsSL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
      -o /usr/local/lib/docker/cli-plugins/docker-compose
    chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
  EOF
}

resource "aws_instance" "data" {
  ami                    = data.aws_ssm_parameter.al2023_ami.value
  instance_type          = "t3.small"
  subnet_id              = aws_subnet.private.id
  vpc_security_group_ids = [aws_security_group.data.id]
  key_name               = aws_key_pair.main.key_name
  user_data              = local.bootstrap_user_data

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
  }

  tags = { Name = "${var.project_prefix}-data" }
}

resource "aws_instance" "app" {
  ami                    = data.aws_ssm_parameter.al2023_ami.value
  instance_type          = "t3.micro" # 필요시 비용 고려하여 t3.medium 등으로 조정 가능하나 이전 설정을 따름
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.app.id]
  key_name               = aws_key_pair.main.key_name
  iam_instance_profile   = aws_iam_instance_profile.app.name
  user_data              = local.bootstrap_user_data

  root_block_device {
    volume_type = "gp3"
    volume_size = 30
  }

  tags = { Name = "${var.project_prefix}-app" }
}