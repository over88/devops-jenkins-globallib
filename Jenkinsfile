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
            def config = readYaml file: './mailRecipient.yml'
            echo config.owner

        } catch (error) {
            echo "Checkout scm on Jenkins master failed!"
            echo "\u001B[31m" + error.message + "\u001B[0m"
            currentBuild.result = "FAILURE"

        }
    }

    stage('Validate configuration') {
        try {

            sh '''
 #!/bin/bash
 set +x
 bad_function=`grep -R System.exit | grep -v Jenkinsfile | awk -F ":" '{print $1'}`
 if [ ! -z "$bad_function" ]; then
    echo "[ERROR!] There are found lines with system.exit. The function has dangerous behaviour for jenkins!"
    echo "Please check that files:"
    echo "$bad_function"
    exit 1
 else
    echo "[INFO!] There are no files with dangerous behaviour for jenkins. Continue..."
 fi

 if [ ! -d "reports" ]; then
   mkdir reports
 else
   rm -rf reports
   mkdir reports   
 fi
touch reports/list_of_unuq_multi_lines.txt reports/full_report.html

 /usr/local/bin/yamllint  -f parsable . | awk '{print $NF,$0}' | sort -nr | cut -f2- -d' ' | sed 's/^/<br> /' | sed 's/$/ <\\/br>/\' >> reports/full_report.html

 all_files=`find . -type f -name "*.yml"`
 for i in $all_files
 do
 short_path=`basename $i`
    if [ "$short_path" == "envMapping.yml" ] || [ "$short_path" == "jobMapping.yml" ]; then
       /usr/local/bin/yamllint -c ./.multi_yamllint -f parsable $i | sed 's/^/<br> /' | sed 's/$/ <\\/br>/\' >> reports/full_report.html
       cat $i | awk -F ":" '{print $1'} | grep -v "\\---" | grep -v "#" | sort | uniq -u >> reports/list_of_unuq_multi_lines.txt
    fi
 done
  full_list_uniq_vars=`cat reports/list_of_unuq_multi_lines.txt | sort | uniq`
  for t in $full_list_uniq_vars
  do
     if [ "$t" == "branch" ] || [ "$t" == "envSettings" ]; then
       test=""
     else
        echo "[ERROR!] $t looks like mistype. Please check it below:"
        grep -R $t | grep -v list_of_unuq_multi_lines.txt | awk -F ':' '{print $1,$2'}       
     fi
 done
 error=`cat reports/list_of_unuq_multi_lines.txt | sort | uniq | grep -v branch | grep -v envSettings | sed \'/^\\s*$/d\'`
 echo $error
 if [ -n "$error" ]; then
    echo "[ERROR!] There are found lines in report. Please check report!"
    exit 1
 else
   echo "[INFO!] All is ok. Continue..."
 fi
 '''
        } catch (error) {
            echo "Something wrong with yaml configuration!"
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
