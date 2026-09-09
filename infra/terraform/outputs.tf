output "ecr_repository_urls" {
  description = "서비스명 -> ECR 리포지토리 URL"
  value       = { for name, repo in aws_ecr_repository.service : name => repo.repository_url }
}

output "vpc_id" {
  value = aws_vpc.main.id
}

output "public_subnet_id" {
  value = aws_subnet.public.id
}

output "private_subnet_id" {
  value = aws_subnet.private.id
}

output "nat_instance_id" {
  value = aws_instance.nat.id
}

output "nat_public_ip" {
  value = aws_instance.nat.public_ip
}

output "private_route_table_id" {
  value = aws_route_table.private.id
}

output "app_sg_id" {
  value = aws_security_group.app.id
}

output "data_sg_id" {
  value = aws_security_group.data.id
}

output "ssh_key_name" {
  value = aws_key_pair.main.key_name
}

output "app_public_ip" {
  value = aws_instance.app.public_ip
}

output "data_private_ip" {
  value = aws_instance.data.private_ip
}
