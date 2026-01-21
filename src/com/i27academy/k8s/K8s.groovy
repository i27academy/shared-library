// have the groovy implementation, a shared lib for our complte ui and apis
package com.i27academy.k8s
class K8s {
    def jenkins

    K8s(jenkins) {
        this.jenkins = jenkins
    }

    // method to connect to gke clusters
    def auth_login(clusterName, zone, projectID) {
        jenkins.sh """
            echo "******************* Authenticating to K8S Cluster ***********"
            gcloud container clusters get-credentials $clusterName --zone $zone --project $projectID
            kubectl get nodes
        """
    }

    // Method to Deploy Applications into k8s 
    def k8sdeploy(k8sManifests_file, docker_image ,namespace){
        jenkins.sh """
            echo "************** Deploying to k8s Cluster *********************"
            ls -la 
            sed -i 's|DIT|${docker_image}|g' ./.cicd/$k8sManifests_file
            kubectl apply -f ./.cicd/$k8sManifests_file -n $namespace
        """

    }

    // cloing the shared library
    def gitClone (){
        jenkins.sh """
            echo "************** Cloning the shared library repo *********************"
            git clone -b main https://github.com/i27academy/shared-library.git
            echo "Listing the files after cloning the repo"
            ls -la 
        """
    }
     
     // Helm Deployment 
    //  def k8sHelmChartDeploy(appName, env){
    //     jenkins.sh """
    //         echo "************** Deploying to k8s Cluster using HELM *********************"
    //         helm install $appName-$env-release CHART_NEEDS_TO_BE_UPDATED -f values.yaml -n $env-namespace
    //     """
    //  }
}
// helm install eureka-dev-release chartname -f values.yaml -n namespace


//gcloud container clusters get-credentials i27-cluster --zone us-central1-a --project proven-wavelet-481608-k1