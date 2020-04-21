/*====
ECS task definitions, this resource is only useful when building a service. It looks for a cluster or service (container)
to resgister task to.
======*/

resource "aws_ecs_cluster" "bod" {
  name = var.ecs_cluster
  setting {
    name = "containerInsights"
    value = "enabled"
  }
  tags = {
    Name = "Viper KCL Cluster"
    Description = var.environment
  }
}

resource "aws_ecs_task_definition" "viper" {
  family = "bd-viperkcl-${var.environment}"
  requires_compatibilities = [
    "FARGATE"]
  network_mode = "awsvpc"
  cpu = "4 vCPU"
  memory = "30 GB"
  execution_role_arn = var.ecs_IAMROLE
  task_role_arn = var.ecs_IAMROLE
  container_definitions = <<EOF
        [
            {
              "name": "bd-viperkcl-definition",
              "image": "${var.image}",
              "essential": true,
              "cpu": 4024,
              "memory": 30000,
              "logConfiguration": {
                "logDriver": "awslogs",
                "options": {
                  "awslogs-group": "viper-kcl-${var.environment}",
                  "awslogs-region": "us-west-2",
                  "awslogs-stream-prefix": "viperkcl"
                }
              }
            }
      ]
  EOF
}

data "aws_ecs_task_definition" "viperkcl" {
  task_definition = aws_ecs_task_definition.viper.family
}

resource "aws_ecs_service" "viperkclservice" {
  name = "bd-viperkcl-service-${var.environment}"
  task_definition = "${aws_ecs_task_definition.viper.family}:${max("${aws_ecs_task_definition.viper.revision}", "${data.aws_ecs_task_definition.viperkcl.revision}")}"
  desired_count = 4
  launch_type = "FARGATE"
  cluster = aws_ecs_cluster.bod.name

  network_configuration {
    security_groups = flatten([
      split(",", var.sg_security_groups[var.environment])])
    subnets = flatten([
      split(",", var.private_subnets[var.environment])])
  }
  platform_version = "1.4.0"


}