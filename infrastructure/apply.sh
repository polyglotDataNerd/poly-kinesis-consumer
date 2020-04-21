#!/usr/bin/env bash
#sampe call
# source ~/poly-kinesis-consumer/infrastructure/environment/consumer_ecs_infra_apply.sh 'production'

AWS_ACCESS_KEY_ID=$(aws ssm get-parameters --names s3AccessKey --profile bod --query Parameters[0].Value --with-decryption --output text)
AWS_SECRET_ACCESS_KEY=$(aws ssm get-parameters --names s3SecretKey --profile bod --query Parameters[0].Value --with-decryption --output text)
CURRENTDATE="$(date +%Y)"
GitToken=$(aws ssm get-parameters --names GitToken --profile bod --query Parameters[0].Value --with-decryption --output text)
EpochTag="$(date +%s)"
GitHash=$(cd ~/poly-kinesis-consumer && (git rev-parse --verify HEAD))
#shell parameter for env.
environment=$1
image=".dkr.ecr.us-west-2.amazonaws.com/kcl-$environment:$EpochTag"

#INFRASTRUCTURE
aws s3 cp s3://poly-utils/kcl/terraform/$environment/infra/$CURRENTDATE ~/poly-kinesis-consumer/infrastructure/infra --profile bod --recursive --sse --quiet --include "*"
# infra variables
export TF_VAR_awsaccess=$AWS_ACCESS_KEY_ID
export TF_VAR_awssecret=$AWS_SECRET_ACCESS_KEY
export TF_VAR_environment=$environment
export TF_VAR_image=$image

cd ~/poly-kinesis-consumer/infrastructure/infra
terraform init
terraform get
terraform validate -check-variables=false
terraform plan
terraform apply -auto-approve

#copy tfstate files to s3
aws s3 cp ~/poly-kinesis-consumer/infrastructure/infra/ s3://poly-utils/kcl/terraform/$environment/infra/$CURRENTDATE/ --profile bod --recursive --sse --quiet --exclude "*" --include "*terraform.tfstate*"

# DOCKER BUILD
cd ~/poly-kinesis-consumer/infrastructure/build
docker build -f Dockerfile \
  -t viperkcl-$environment:$EpochTag -t bd-viperkcl-$environment:$EpochTag \
  --build-arg AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID \
  --build-arg AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY \
  --build-arg GitToken=$GitToken \
  --build-arg GitHash=$GitHash \
  --build-arg environment=$environment \
  --force-rm \
  --no-cache .
docker tag viperkcl-$environment:$EpochTag $image
eval "$(aws ecr get-login --profile bod --region us-west-2 --no-include-email)"
docker push $image

# APPLICATION SERVICE
aws s3 cp s3://poly-utils/kcl/terraform/$environment/app/$CURRENTDATE ~/poly-kinesis-consumer/infrastructure/app --profile bod --recursive --sse --quiet --include "*"

# app variables
export TF_VAR_awsaccess=$AWS_ACCESS_KEY_ID
export TF_VAR_awssecret=$AWS_SECRET_ACCESS_KEY
export TF_VAR_environment=$environment
export TF_VAR_image=$image

cd ~/poly-kinesis-consumer/infrastructure/app
terraform init
terraform get
terraform validate -check-variables=false
terraform plan
terraform apply -auto-approve

#copy tfstate files to s3
aws s3 cp ~/poly-kinesis-consumer/infrastructure/app/ s3://poly-utils/kcl/terraform/$environment/app/$CURRENTDATE/ --profile bod --recursive --sse --quiet --exclude "*" --include "*terraform.tfstate*"

cd ~/poly-kinesis-consumer/
