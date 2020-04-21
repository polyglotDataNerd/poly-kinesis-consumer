/*====
This terraform build can only run once if environments persist. This builds the service that the consumer task will run in
We can use the apply command to rebuild and the destroy command to delete all the environments in terraform
======*/

/*====
Cloudwatch Log Group
======*/
resource "aws_cloudwatch_log_group" "kcl_log_group" {
  name = "viper-kcl-${var.environment}"
  tags = {
    Environment = var.environment
    Application = "viperkcl"
  }
  retention_in_days = 14

}

/* https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/FilterAndPatternSyntax.html */
resource "aws_cloudwatch_log_metric_filter" "kcl_error" {
  log_group_name = aws_cloudwatch_log_group.kcl_log_group.name
  name = "viperError-kcl-${var.environment}"
  pattern = "ERROR"
  metric_transformation {
    name = "KCLErrorCount"
    namespace = "ViperKCL"
    value = "1"
  }
}

/*====
ECR repository to store our Docker images
======*/
resource "aws_ecr_repository" "viperkcl" {
  name = "${var.repository_name}-${var.environment}"
}
