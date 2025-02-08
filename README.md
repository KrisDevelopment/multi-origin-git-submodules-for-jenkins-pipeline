# multi-origin-git-submodules-for-jenkins-pipeline
Multi-origin git submodules checkout procedure for jenkins pipelines.
This is just a snippet of groovy script - copy and paste it into your own pipeline.
The way it works is it explicitly sets the repo URL and credentials to pull the submodule from when relying on HTTPS. It will elevate the submodule to the latest HEAD of its primary branch. The process will therefore reattach any detached submodule HEAD.
In Jenkins you'd want to go in and configure credentials with IDs that match the provider-credentials dictionary of the script.