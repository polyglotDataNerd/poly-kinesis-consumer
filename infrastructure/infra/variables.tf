variable "awsaccess" {}
variable "awssecret" {}
variable "environment" {
  description = "env will be passed as an arguement in the build"
}
variable "image" {
  type = "string"
  description = "ecs repo image name"
}

variable "region" {
  description = "Region that the instances will be created"
  default = "us-west-2"
}

variable "availability_zone" {
  type = "list"
  description = "The AZ that the resources will be launched"
  default = [
    "us-west-2a",
    "us-west-2b",
    "us-west-2c"]
}

# Networking

variable "vpc_cidr" {
  description = "The CIDR block of the VPC"
  default = "10.0.0.0/16"
}

variable "private_subnets" {
  description = "sg data private subnets"
  type = "map"
  default = {
    us-west-2-prod = ""
    us-west-2-dev4 = ""
  }
}

variable "public_subnets" {
  description = "The private subnets to use"

  type = "map"
  default = {
    us-west-2-prod = ""
    us-west-2-dev4 = ""
  }
}

variable "sg_security_groups" {
  description = "sg security groups"
  type = "map"
  default = {
    us-west-2-prod = ""
    us-west-2-dev4 = ""
  }
}

variable "ecs_IAMROLE" {
  description = "The IAM role for the container"
  type = "string"
  default = ""
}

variable "repository_name" {
  description = "repository name for container images"
  type = "string"
  default = "KCL"
}

variable "ecr_account_path" {
  description = "ecr path for data aws account"
  type = "string"
  default = ""
}

variable "ecs_cluster" {
  description = "ecs clutser"
  type = "string"
  default = "poly-cluster"
}
