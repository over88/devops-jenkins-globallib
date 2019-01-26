def call(Closure body) {
    //init
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    //vars
    recipients = config.recipients
    recipient_users_list = []
    final_recipients = []
    //DEBUG
    if(config.recipients){
        log.info("jenkinsNotification: got the '${config.recipients}' as email-addreses")
    }
    //END OF DEBUG
    if(env.NODE_NAME){
        try {
            if (config.recipient_yml_path){
                recipients_file = readYaml file: config.recipient_yml_path
            } else {
                if (fileExists('./recipients.yml')) {
                    recipients_file =  readYaml file: './recipients.yml'
                }
            }
            if (fileExists('./recipients.yml')) {
                try {
                    groups =  config.recipient_groups.split('\\s*,\\s*')
                    for (it in groups) {
                        recipient_users_list << recipients_file[it]
                    }
                } catch(NullPointerException ex){
                    recipient_users_list = new ArrayList(recipients_file.values())
                }
                println(recipient_users_list)
            }
        } catch(FileNotFoundException err){
            log.info('jenkinsNotification: There is no recipient_yml file')
        }
    }
    try {
        for (it in recipients.split('\\s*,\\s*')){recipient_users_list << it}
        log.info ('jenkinsNotification: recipients found')
    } catch(NullPointerException ex){
        log.info ('jenkinsNotification: recipients are empty')
    }
    for (i in recipient_users_list) {
        for (j in i.split('\\s*,\\s*')) {
            final_recipients << j
        }
    }
    log.info('jenkinsNotification: size of all attachments should be less then 8Mb')
    if(!env.NODE_NAME){
        log.info('jenkinsNotification: running outside the node, files will not be attached')
    }
    //closures
    def windowsreader = {multilineText ->
        result_arr = []
        temp_arr = multilineText.split('\n')
        for(i in temp_arr){
            if(i != temp_arr[0]){
                result_arr << i
            }
        }
        return result_arr.join('\n')
    }

    def changeLogSets = { ->
        return currentBuild.changeSets
    }

    def changeLog = { ->
        def change_log = """<tr><td class="bodyContent fullContent">
<h1>Git Commit Changes</h1>
</td>
</tr>
<tr>
<td valign="top" class="bodyContent  fullContent">
<pre>"""
        for (int i = 0; i < changeLogSets().size(); i++) {
            def entries = changeLogSets()[i].items
            for (int j = 0; j < entries.length; j++) {
                def entry = entries[j]
                change_log += "${entry.commitId} by ${entry.author} on ${new Date(entry.timestamp)}: ${entry.msg}\n"
                def files = new ArrayList(entry.affectedFiles)
                for (int k = 0; k < files.size(); k++) {
                    def file = files[k]
                    change_log += "  ${file.editType.name} ${file.path}\n"
                }
            }
        } 
        change_log+='</pre></td></tr>'
        if (changeLogSets().size() > 0) {return change_log} else {""}
    }

    def buildUser = { ->
        if(env.NODE_NAME){
            build_user = wrap([$class: 'BuildUser']) { env.BUILD_USER }
            build_user_id = wrap([$class: 'BuildUser']) { env.BUILD_USER_ID }
            if (build_user && build_user_id){
                return  """
                <tr>
                <td valign="top" class="utilityLinkContent">
                <a href="${env.JENKINS_URL+'/user/'+build_user_id}" target="_blank">Started by ${build_user}</a>
                </td></tr>"""
            } else {return ""}
        } else {return ""}
    }

    def gitUrl = {->
        def gitRepo
        def gitCommit
        def switchToNewBranch
        if(env.NODE_NAME){
            if (fileExists('.git')){
                if (isUnix()){
                    gitCommit = sh(returnStdout: true, script: 'git log --pretty=format:"%H" -n 1').trim()
                    gitRepo = sh(returnStdout: true, script: 'git config --get remote.origin.url').trim()
                }  else {
                    log.info('jenkinsNotification: Windows slave detected')
                    try{
                        gitCommit = windowsreader(bat(returnStdout: true, script: "git log --pretty=format:%%H -n 1").trim())    
                    } catch(e) {
                        log.warning('jenkinsNotification: cannot get git commit')
                    }
                    gitRepo = windowsreader(bat(returnStdout: true, script: 'git config --get remote.origin.url').trim())
                }
            }
            if (gitRepo && gitCommit){
                git_url = gitRepo[0..-5]+"/commit/"+gitCommit
                return """<tr><td class="bodyContent fullContent">
                <h1>Git Commit Info</h1>
                </td>
                </tr>
                <tr>
                <td valign="top" class="bodyContent  fullContent">
                <a href="${git_url}" target="_blank">${git_url}</a>
                </td></tr>"""}
            else {return ""}
                 }
        else {return ""}
    }

    def deploy_template = {app_urls->
        if (app_urls) {
            htmlUrlsTemplete = """"""
            for (url in config.app_urls.split('\\s*,\\s*')) {
                htmlUrlsTemplete = htmlUrlsTemplete + """
                <tr>
                <td valign="top" class="utilityLinkContent">
                <a href="${url}" target="_blank">${url}</a>
                </td>
                </tr>"""}
            return """<tr>
      <td class="bodyContent leftColumnContent">
        <h1>Applications</h1>
      </td>
    </tr>
    <tr>
      <td valign="top" class="leftColumnContent">
        <table border="0" cellpadding="5" cellspacing="0" id="utilityLink">
        ${htmlUrlsTemplete}
        </table>
      <td>
    </tr>"""
    } else {""}}

    def message = {text -> 
        if (text !=null){
        """ <hr>
            <table border="0" cellpadding="10" cellspacing="0" width="90%">
               <tr>
                  <td valign="top" class="bodyContent  fullContent">
                     <pre>${text}</pre>
                  </td>
               </tr>
            </table>"""
        } else {""}}

    def mail_type = {
        text ->
        if (text == 'build_started'){
            color = "black"
            textcolor = "#ea0c0c"
            subject = env.BUILD_TAG + " - Building!"
            greetings  = "Jenkins build started"
            logattach  = false
        } else if (text == 'build_complete'){
            color = "black"
            textcolor = "#ea0c0c"
            subject = env.BUILD_TAG + ' - ' + currentBuild.result+ "!"
            greetings  = "Jenkins pipeline complete - " + currentBuild.result
            logattach  = true
        } else if (text == 'test_result'){
            color = "black"
            textcolor = "#ea0c0c"
            subject = env.BUILD_TAG + " Test Run Complete - " + currentBuild.result
            greetings  = "Test Run Complete"
            logattach  = true
        } else if (text == 'deploy'){
            if (!config.app_name){
                throw new IOException("jenkinsNotification: App name is not set, 'app_name' and 'env' are required if type is 'deploy'", config.app_name)
            }
            if (!config.env ) {
                throw new IOException("jenkinsNotification: Env name is not set, , 'app_name' and 'env' are required if type is 'deploy'", config.env)
            }
            color = "black"
            textcolor = "#ea0c0c"
            subject = env.BUILD_TAG + ' - ' + config.app_name + ' deployed to ' + config.env + ' - ' + currentBuild.result+ "!"
            greetings  = "Jenkins deploy - " + currentBuild.result
            logattach  = true
            if (config.env == 'production') {final_recipients << 'change-mgmt@corp.idt.net,eytan.faverman+prodDeploy@idt.net'}
        } else if (text == 'run_test'){
            if (!config.device_env){
                throw new IOException("jenkinsNotification: Device Type is not set, 'device_env' and 'execution_type' are required if type is 'run_test'", config.device_env)
            }
            if (!config.execution_type ) {
                throw new IOException("jenkinsNotification: Execution Type is not set, 'device_env' and 'execution_type' are required if type is 'run_test'", config.execution_type)
            }
            color = "black"
            textcolor = "#ea0c0c"
            subject = env.BUILD_TAG + ' - ' + config.device_env + ' ' + config.execution_type + ' - ' + currentBuild.result + "!"
            greetings  = "Jenkins Test Execution - " + currentBuild.result
            logattach  = true            
        } else {
            throw new IOException("jenkinsNotification: Template isn't set")
        }
        return """
       <table width="100%" style="table-layout: fixed; margin: 0 auto;background-color:${color};" border="0" cellspacing="0" cellpadding="5">
         <tr>
           <td align="left">
              <div id="header">
                 <div class="logo">
                   <a id="jenkins-home-link" href="${env.JENKINS_URL}">
                    <span class="jenkins-head-icon" alt="jenkins" style="background: url(https://wiki.jenkins-ci.org/download/attachments/327683/JENKINS?version=1&modificationDate=1302750804000) no-repeat;height: 48px; display: inline-block; padding: 0; text-align: left; width: 48px; float: left;"></span>
                    <span class="jenkins-name-icon" alt="jenkins" height="48" alt="title" width=90% style="height: 40px; display: inline-block; padding: 0; text-align: left; color: ${textcolor}; display: block; padding: 10px 0px 0px 10px; text-align: left; width: 90%; float: left; font-size: 22px;">${greetings}</span>
                    </a>
                 </div>
              </div>
           </td>
        </tr>
     </table>
   """}


    skeleton = """
<html>
<head>
   <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
   <meta name="viewport" content="width=device-width, initial-scale=1">
   <meta name="format-detection" content="telephone=no">
   <meta http-equiv="X-UA-Compatible" content="IE=edge" />
   <title>Jenkins Deploy</title>
   <style type="text/css">
      .bodyContent {
      color: #5f5f5f;
      font-family: Helvetica, Arial, sans-serif;
      font-size: 14px;
      line-height: 145%;
      }
      h1 {
      line-height: 1em;
      }
      .footerContent,
      .utilityLinkContent {
      color: #5f5f5f;
      font-family: Helvetica, Arial, sans-serif;
      font-size: 13px;
      line-height: 125%;
      }
      .footerContent a,
      .utilityLinkContent a {
      color: #2196F3;
      }
      @media only screen and (max-width: 480px) {
      #templateColumns {
      width: 100% !important;
      }
      .bodyContent {
      font-size: 16px !important;
      }
      .templateColumnContainer {
      display: block !important;
      width: 100% !important;
      }
      .columnImage {
      height: auto !important;
      max-width: 480px !important;
      width: 100% !important;
      }
      .leftColumnContent {
      font-size: 16px !important;
      line-height: 125% !important;
      }
      .rightColumnContent {
      font-size: 16px !important;
      line-height: 125% !important;
      }
      #utilityLink {
      max-width: 600px !important;
      width: 100% !important;
      }
      .utilityLinkContent {
      background-color: #E1E1E1 !important;
      border-bottom: 10px solid #FFFFFF;
      display: block !important;
      font-size: 15px !important;
      padding: 15px 0 !important;
      text-align: center !important;
      width: 100% !important;
      }
      .utilityLinkContent a {
      color: #2196F3 !important;
      display: block !important;
      text-decoration: none !important;
      }
      }
      .jenkins-head-icon {
      background: url(https://wiki.jenkins-ci.org/download/attachments/327683/JENKINS?version=1&modificationDate=1302750804000) no-repeat;
      height: 48px;
      display: inline-block;
      padding: 0;
      text-align: left;
      width: 48px;
      float: left;
      }
      .jenkins-name-icon {
      height: 40px;
      display: inline-block;
      padding: 0;
      text-align: left;
      color: #ea0c0c;
      display: block;
      padding: 10px 0px 0px 10px;
      text-align: left;
      width: 179px;
      float: left;
      font-size: 22px;
      }
   </style>
</head>
<body topmargin="0" bottommargin="0" leftmargin="0" rightmargin="0" bgcolor="#000" style="background:#000;margin:0;padding:0;">
   ${mail_type(config.type)}
   <table width="100%" style="table-layout: fixed; margin: 0 auto;background:#fff;" bgcolor="#fff" border="0" cellspacing="0" cellpadding="0">
      <tr>
         <td align="center">
            <table border="0" cellpadding="0" cellspacing="0" width="90%" id="templateColumns">
               <tr>
                  <td align="center" valign="top" width="80%" class="templateColumnContainer">
                     <table border="0" cellpadding="10" cellspacing="0" width="100%">
                     ${deploy_template(config.app_urls)}
                     </table>
                  </td>
               </tr>
            </table>
            <hr>
            <table border="0" cellpadding="0" cellspacing="0" width="90%" id="templateColumns">
               <tr>
                  <td align="center" valign="top" width="80%" class="templateColumnContainer">
                     <table border="0" cellpadding="10" cellspacing="0" width="100%">
                        <tr>
                           <td class="bodyContent leftColumnContent">
                              <h1>Build Info</h1>
                           </td>
                        </tr>
                        <tr>
                           <td valign="top" class="leftColumnContent">
                              <table border="0" cellpadding="5" cellspacing="0" id="utilityLink">
                                 <tr>
                                    <td valign="top" class="utilityLinkContent" colspan="2">
                                     <a href="${env.BUILD_URL}console" target="_blank">View console output</a>
                                     <br>
                                     <a href="${env.RUN_DISPLAY_URL}" target="_blank">Blue Ocean</a>
                                    </td>
                                 </tr>
                                 ${buildUser()}
                              </table>
                           </td>
                        </tr>
                     </table>
                  </td>
               </tr>
            </table>
            ${message(config.message)}
            <hr>
            <table border="0" cellpadding="10" cellspacing="0" width="90%">
                 ${gitUrl()}
                 ${changeLog()}
            </table>
         </td>
      </tr>
   </table>
</body>
</html>"""
    emailext attachLog: logattach, attachmentsPattern: config.test_result_files, mimeType: 'text/html', body: skeleton, subject: subject , to: final_recipients.join(",")
}
