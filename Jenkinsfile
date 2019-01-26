#!groovy
import static groovy.io.FileType.FILES

def my_change_log
def jenkinsHost
def workflowLibsDirEcs = "/var/jenkins_home/workflow-libs/"
def workflowLibsDir
def map_vars

node('master') {
    stage('Checkout new resources') {
        try {
            map_vars = checkout scm
            my_change_log = sh(returnStdout: true, script: 'git log --pretty=format:"%cN %s" -n1').trim()
            jenkinsHost = sh(returnStdout: true, script: "hostname")
            workflowLibsDir = workflowLibsDirEcs

            echo "My workflowLibsDir is: $workflowLibsDir"

            sh "rm -rf .git"
            echo config.owner

        } catch (error) {
            echo "Checkout scm on Jenkins master failed!"
            echo "\u001B[31m" + error.message + "\u001B[0m"
            currentBuild.result = "FAILURE"

        }
    }

    stage('Deploy new resourses') {
        try {
            echo "[INFO!] Jenkins-ECS cluster and master branch are detected. Continue..."
            echo "[INFO!] Copying files to $workflowLibsDir"
            sh "rsync -avz --delete . $workflowLibsDir"
            publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: true, reportDir: 'reports', reportFiles: 'full_report.html', reportName: 'Full HTML Report', reportTitles: ''])
            sh '''#!/bin/bash
set +x
if [ -s reports/full_report.html ]
then
   echo "[ERROR!] You have wrong yaml files! Please check report"
   exit 1             
else
    echo "[INFO!] You do not have wrong yaml files! All is OK. Continue..."
fi
'''
            currentBuild.result = "SUCCESS"
        } catch (error) {
            echo "Copy to $workflowLibsDir on Jenkins master failed!"
            echo "\u001B[31m" + error.message + "\u001B[0m"
            currentBuild.result = "FAILURE"

        }

        jenkinsNotification {
            recipients = 'fa.anatoly@gmail.com'
            type = "deploy"
            app_urls = "https://github.com/over88/jenkins-gloaballib.git"
            app_name = "devops-jenkins-globallib"
            env = "Production"
            message = "Deploy files to $workflowLibsDir on Jenkins master - HOST: $jenkinsHost."
        }
    }
        stage('Archiving current resourses') {
            archiveArtifacts artifacts: '**/*.*', defaultExcludes: false, excludes: '.git', fingerprint: true
        }
}
