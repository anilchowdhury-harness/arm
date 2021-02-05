private void printStatus(DeploymentOperation operation) {
    /*TargetResource targetResource = operation.targetResource();
    String provisioningState = operation.provisioningState();
    Object statusMessage = operation.statusMessage();
    System.out.println(String.format("Target Resource - [%s]", targetResource.resourceName()));
    System.out.println(String.format("Provisioning State - [%s]", provisioningState));
    System.out.println(String.format("Status Code - [%s]%n", operation.statusCode()));
    if (statusMessage != null) {
      System.out.println(String.format("Status - [%s]", statusMessage));
    }*/

    if (operation.targetResource() != null) {
      System.out.println(String.format("%s - %s: %s %s", operation.targetResource().resourceType(),
          operation.targetResource().resourceName(), operation.provisioningState(),
          operation.statusMessage() != null ? operation.statusMessage() : ""));
    }
  }
