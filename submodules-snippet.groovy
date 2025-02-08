// This is just a snippet of groovy script - copy and paste it into your own pipeline.
// The way it works is it explicitly sets the repo URL and credentials to pull the submodule from when relying on HTTPS. It will elevate the submodule to the latest HEAD of its primary branch. The process will therefore reattach any detached submodule HEAD.
// In Jenkins you'd want to go in and configure credentials with IDs that match the provider-credentials dictionary of the script.


// define credential paris. provider -> credentialId

credentials = [
    'github.com': 'github-credentials', // these IDs should be defined in Jenkins
    'gitlab.com': 'gitlab-credentials',
    // any other provider here in the form ('server': 'credentials-id')
]

def stripProtocol(url){
    return url.replaceFirst("https://", "").replaceFirst("http://", "").replaceFirst("git@", "").replaceFirst("ssh://", "").replaceFirst("git://", "").replaceFirst("://", "")
}

gitRepoNoProtocol = stripProtocol(gitRepo)

def getProviderFrom(url){
    // replace http and https
    url = url.replaceAll('https://', '')
    url = url.replaceAll('http://', '')

    // replace ssh
    url = url.replaceAll('git@', '')

    // split by slash
    def parts = url.split('/')
    return parts[0]
}

def scmCheckout(){
    // SCM chechkout with submodules
    // Attempt checkout with retry in case of network issues
    retry(3) {
        scmCredentials = credentials[getProviderFrom(gitRepo)]
        
        echo "Credentials: ${scmCredentials} for provider ${getProviderFrom(gitRepo)}. Url ${gitRepo}"
        echo "SCM_BRANCH: ${params.SCM_BRANCH}"
        echo "SCM_REPO: ${params.SCM_REPO}"

        checkout([
            $class: 'GitSCM',
            branches: [[name: scmBranch]],
            extensions: [
                [$class: 'CheckoutOption', timeout: scmTimeout],
                [$class: 'SubmoduleOption', 
                    disableSubmodules: true, 
                    parentCredentials: false,  // Set to false to prevent root credentials from being used for submodules
                    recursiveSubmodules: false, // Handle submodule initialization manually for more control
                    reference: '', 
                    timeout: scmTimeout, 
                    trackingSubmodules: true
                ],
                [$class: 'CleanBeforeCheckout', deleteUntrackedNestedRepositories: true]
            ],
            userRemoteConfigs: [[credentialsId: scmCredentials, url: gitRepo]]
        ])
    }

    submodules_paths = sh(script: 'git config --file .gitmodules --get-regexp path | awk \'{ print $2 }\'', returnStdout: true).trim().split('\n')
    submodule_remotes = []
    submodules_paths.each { path ->
        remote = sh(script: "git config --file .gitmodules --get submodule.${path}.url", returnStdout: true).trim()
        submodule_remotes.add(remote)
    }

    for (int i = 0; i < submodules_paths.size(); i++) {
        submodule_path = submodules_paths[i]
        submodule_remote = submodule_remotes[i]

        echo "Checking out submodule ${submodule_path} from ${submodule_remote}"
        retry(3) {
            // Explicitly handle submodule initialization and updates
            echo 'Initializing and updating submodules:'
            withCredentials([
                usernamePassword(
                    credentialsId: credentials[getProviderFrom(submodule_remote)], 
                    usernameVariable: 'SELFHOSTED_USER', 
                    passwordVariable: 'SELFHOSTED_PASS'
                )
            ]) {
                remote_no_protocol = stripProtocol(submodule_remote)

                sh """
                    # Initialize and fetch submodules individually
                    git config submodule.${submodule_path}.url https://${SELFHOSTED_USER}:${SELFHOSTED_PASS}@${remote_no_protocol}.git
                    
                    git submodule set-url ${submodule_path} https://${SELFHOSTED_USER}:${SELFHOSTED_PASS}@${remote_no_protocol}.git
                    git submodule sync --recursive
                    
                    # Initialize the submodule and make sure it's on latest HEAD
                    git submodule update --init --recursive --merge --remote ${submodule_path}

                    # Make sure the submodule is on the correct branch fix the detached HEAD state
                    git -C ${submodule_path} checkout \$(git -C ${submodule_path} remote show origin | awk '/HEAD branch/ {print \$NF}')

                    # Remove the credentials file
                    rm ~/.git-credentials || true
                """
                
            }

        }
    }

    // Print revision of each submodule
    echo 'Submodule revisions:'
    sh 'git submodule foreach git rev-parse HEAD'

}

