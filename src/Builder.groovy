def call (pipeline, notifier) {
    echo "Begin buildProject"
    //echo pipeline.repoSettings.slackChannel
    def branchName = env.BRANCH_NAME
    def buildNumber = env.BUILD_NUMBER
    def gitCommit = env.GIT_COMMIT
    def jenkinsBuildTag = env.BUILD_TAG
    echo "branchName=$branchName buildNumber=$buildNumber gitCommit=$gitCommit jenkinsBuildTag=$jenkinsBuildTag"
    buildDocker(pipeline, branchName, buildNumber, gitCommit, jenkinsBuildTag )
    echo "End buildProject"
}

def buildDocker(pipeline, branchName, buildNumber, gitCommit, jenkinsBuildTag ){

    withAWSTerraformQa {
        def awsAccount = pipeline.repoSettings.qaAwsAccount
        echo "AwsAccount=$awsAccount"

        echo "In - buildDocker: awsAccount=$awsAccount branchName=$branchName buildNumber=$buildNumber gitCommit=$gitCommit jenkinsBuildTag=$jenkinsBuildTag"
        sh("./build-docker.sh -a ${awsAccount} -b ${branchName} -n ${buildNumber} -g ${gitCommit} -j ${jenkinsBuildTag}")

        dir ('terraform') {
            withEnv(["PATH+TERRAFORM=${tool 'Terraform_0.11.7'}"]) {
                if (fileExists('terraform.tfstate')) {
                    echo("Remove tfstate")
                    sh('rm terraform.tfstate')
                }
                sh("./updateRepositoryUrl.sh ${awsAccount} ${jenkinsBuildTag} qaosi")
            }
        }
    }
}

return this
