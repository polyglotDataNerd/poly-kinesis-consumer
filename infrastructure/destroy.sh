#!/usr/bin/env bash
#sampe call
# source ~/poly-kinesis-consumer/infrastructure/environment/consumer_ecs_infra_destroy.sh 'production'


AWS_ACCESS_KEY_ID=$(aws ssm get-parameters --names s3AccessKey --profile bod --query Parameters[0].Value --with-decryption --output text)
AWS_SECRET_ACCESS_KEY=$(aws ssm get-parameters --names s3SecretKey --profile bod --query Parameters[0].Value --with-decryption --output text)
CURRENTDATE="$(date  +%Y)"
#shell parameter for env.
environment=$1

# INFRASTRUCTURE
aws s3 cp s3://poly-utils/kcl/terraform/$environment/infra/$CURRENTDATE ~/poly-kinesis-consumer/infrastructure/infra  --profile bod --recursive --sse --quiet --include "*"

export TF_VAR_awsaccess=$AWS_ACCESS_KEY_ID
export TF_VAR_awssecret=$AWS_SECRET_ACCESS_KEY
export TF_VAR_environment=$environment
export TF_VAR_image=$image

cd ~/poly-kinesis-consumer/infrastructure/infra
terraform init
terraform destroy -auto-approve

#copy tfstate files to s3
aws s3 cp ~/poly-kinesis-consumer/infrastructure/infra/ s3://poly-utils/kcl/terraform/$environment/infra/$CURRENTDATE/  --profile bod --recursive --sse --quiet --exclude "*" --include "*terraform.tfstate*"


# APPLICATION SERVICE
aws s3 cp s3://poly-utils/kcl/terraform/$environment/app/$CURRENTDATE ~/poly-kinesis-consumer/infrastructure/app  --profile bod --recursive --sse --quiet --include "*"

export TF_VAR_awsaccess=$AWS_ACCESS_KEY_ID
export TF_VAR_awssecret=$AWS_SECRET_ACCESS_KEY
export TF_VAR_environment=$environment
export TF_VAR_image=$image

cd ~/poly-kinesis-consumer/infrastructure/app
terraform init
terraform destroy -auto-approve

#copy tfstate files to s3
aws s3 cp ~/poly-kinesis-consumer/infrastructure/app/ s3://poly-utils/kcl/terraform/$environment/app/$CURRENTDATE/  --profile bod --recursive --sse --quiet --exclude "*" --include "*terraform.tfstate*"

cd ~/poly-kinesis-consumer/
