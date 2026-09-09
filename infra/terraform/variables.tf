//지역
variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

variable "project_prefix" {
  description = "기존 계정 리소스와 구분하기 위한 네이밍 prefix"
  type        = string
  default     = "sixfin"
}

variable "my_ip" {
  description = "app-sg 인바운드(SSH/Gateway/Zipkin)를 제한할 본인 공인 IP. CIDR 형식 (예: 1.2.3.4/32)"
  type        = string
}

variable "ssh_public_key" {
  description = "aws_key_pair에 등록할 SSH 공개키 내용 (개인키는 다루지 않는다)"
  type        = string
}


variable "service_names" {
  description = "ECR 리포지토리를 만들 배포 대상 11개 서비스"
  type        = list(string)
  default = [
    "eureka-server",
    "gateway-service",
    "user-service",
    "trading-service",
    "learning-service",
  ]
}