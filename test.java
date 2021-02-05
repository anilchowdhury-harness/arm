private void steadyStateCheck(Azure azureClient, String resourceGroup, String deploymentName)
      throws InterruptedException {
    Deployment deployment = azureClient.deployments().getByResourceGroup(resourceGroup, deploymentName);

    while (!deployment.provisioningState().equalsIgnoreCase("Succeeded")) {
      System.out.println(
          String.format("%nDeployment Status for - [%s] is [%s]", deploymentName, deployment.provisioningState()));
      PagedList<DeploymentOperation> deploymentOperations = deployment.deploymentOperations().list();

      deploymentOperations.forEach(this::printStatus);

      Thread.sleep(1000);
      deployment = azureClient.deployments().getByResourceGroup(resourceGroup, deploymentName);
    }
  }
