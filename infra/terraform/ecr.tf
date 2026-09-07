# Phase 2 — ECR 리포지토리 11개 (ai-service는 B-5로 보류, 제외)

resource "aws_ecr_repository" "service" {
  for_each = toset(var.service_names)

  name                 = "${var.project_prefix}/${each.value}"
  image_tag_mutability = "MUTABLE"

  # 이미지가 남아 있는 리포지토리는 기본적으로 삭제가 거부된다
  # (RepositoryNotEmptyException). 시연 종료 후 destroy가 중간에 멈추지 않도록
  # 이미지째 삭제를 허용한다. 1주 시연용이라 보존할 이미지가 없다.
  force_delete = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

# 이미지 3개만 유지 — 포트폴리오용 단기 배포라 스토리지 비용 관리 목적
resource "aws_ecr_lifecycle_policy" "service" {
  for_each = aws_ecr_repository.service

  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "keep last 3 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 3
      }
      action = { type = "expire" }
    }]
  })
}