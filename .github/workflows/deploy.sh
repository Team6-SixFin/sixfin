#!/bin/bash
set -e
cd /home/ec2-user/sixfin

ECR_REGISTRY="496043249471.dkr.ecr.ap-northeast-2.amazonaws.com"

echo "=== 인프라 설정 파일 최신화 ==="
git fetch origin main
git checkout origin/main -- docker-compose.app.yml docker-compose.data.yml prometheus.yml 2>/dev/null || true

echo "=== ECR 로그인 ==="
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin $ECR_REGISTRY

echo "=== 이미지 pull ==="
docker compose -f docker-compose.app.yml --env-file .env pull

echo "=== 컨테이너 재생성 ==="
docker compose -f docker-compose.app.yml --env-file .env up -d --force-recreate

echo "=== 헬스체크 ==="
sleep 30
HEALTHY=false
for i in $(seq 1 15); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:19090/actuator/health || echo 000)
  if [ "$STATUS" = "200" ]; then
    echo "HEALTHCHECK_OK"
    HEALTHY=true
    break
  fi
  echo "재시도 $i/15 (상태: $STATUS)"
  sleep 10
done

if [ "$HEALTHY" = "false" ]; then
  echo "HEALTHCHECK_FAIL"
  exit 1
fi

echo "=== 배포된 이미지 digest 확인 ==="
for svc in eureka-server gateway-service trading-service user-service learning-service; do
  DIGEST=$(docker inspect $ECR_REGISTRY/sixfin/$svc:latest --format='{{index .RepoDigests 0}}' 2>/dev/null)
  echo "$svc: $DIGEST"
done

echo "=== 배포 완료 ==="