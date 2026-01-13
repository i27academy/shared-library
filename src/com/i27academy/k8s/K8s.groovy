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
    def k8sdeploy(){
        jenkins.sh """
            echo "************** Deploying to k8s Cluster *********************"
        """

    }

    // method to connect to eks clusters
}


//gcloud container clusters get-credentials i27-cluster --zone us-central1-a --project proven-wavelet-481608-k1