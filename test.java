package software.wings.helpers.ext.azure;

import static io.harness.azure.model.AzureConstants.SLOT_SWAP_JOB_PROCESSOR_STR;

import static com.microsoft.azure.management.resources.WhatIfResultFormat.FULL_RESOURCE_PAYLOADS;

import io.harness.serializer.JsonUtils;

import software.wings.beans.AzureConfig;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.appservice.CsmSlotEntity;
import com.microsoft.azure.management.appservice.DeploymentSlot;
import com.microsoft.azure.management.appservice.DeploymentSlots;
import com.microsoft.azure.management.appservice.Experiments;
import com.microsoft.azure.management.appservice.FunctionApp;
import com.microsoft.azure.management.appservice.FunctionApps;
import com.microsoft.azure.management.appservice.RampUpRule;
import com.microsoft.azure.management.appservice.SiteConfig;
import com.microsoft.azure.management.appservice.WebApp;
import com.microsoft.azure.management.appservice.WebApps;
import com.microsoft.azure.management.appservice.implementation.SiteConfigResourceInner;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet;
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVM;
import com.microsoft.azure.management.monitor.EventData;
import com.microsoft.azure.management.resources.Deployment;
import com.microsoft.azure.management.resources.DeploymentMode;
import com.microsoft.azure.management.resources.DeploymentOperation;
import com.microsoft.azure.management.resources.DeploymentOperationProperties;
import com.microsoft.azure.management.resources.DeploymentProperties;
import com.microsoft.azure.management.resources.DeploymentWhatIf;
import com.microsoft.azure.management.resources.DeploymentWhatIfProperties;
import com.microsoft.azure.management.resources.DeploymentWhatIfSettings;
import com.microsoft.azure.management.resources.ParametersLink;
import com.microsoft.azure.management.resources.TemplateLink;
import com.microsoft.azure.management.resources.implementation.DeploymentExtendedInner;
import com.microsoft.azure.management.resources.implementation.DeploymentInner;
import com.microsoft.azure.management.resources.implementation.DeploymentOperationInner;
import com.microsoft.azure.management.resources.implementation.DeploymentOperationsInner;
import com.microsoft.azure.management.resources.implementation.DeploymentsInner;
import com.microsoft.azure.management.resources.implementation.WhatIfOperationResultInner;
import com.microsoft.rest.ServiceCallback;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import rx.Completable;
import rx.Subscriber;
import rx.schedulers.Schedulers;

/**
 * azure.webApps().getByResourceGroup("devAppServiceAPIResourceGroup", "devWebAppWar")
 * azure.webApps().getByResourceGroup("devAppServiceAPIResourceGroup", "devWebAppWar").inner().outboundIpAddresses()
 * azure.webApps().getByResourceGroup("devAppServiceAPIResourceGroup", "devWebAppWar").appServicePlanId()
 *
 * azure.appServices().appServicePlans().getByResourceGroup("devAppServiceResourceGroup","devAppServiceFreePlan")
 * azure.appServices().appServicePlans().inner().getByResourceGroup("devAppServiceResourceGroup",
 * "devAppServiceFreePlan")
 *
 * azure.webApps().getByResourceGroup("devAppServiceAPIResourceGroup", "devWebAppWar").inner().enabledHostNames()
 * azure.webApps().getByResourceGroup("devAppServiceAPIResourceGroup", "devWebAppWar").enabledHostNames() -> may be
 * hostname should be enough for CV
 *
 * az webapp list-instances -n functionAppDocker -g devAppServiceAPIResourceGroup
 * azure.webApps().inner().listInstanceIdentifiers("devAppServiceAPIResourceGroup", "functionAppDocker")
 *
 *
 * azure.appServices().webApps().inner().listHostNameBindings("devAppServiceAPIResourceGroup", "functionAppDocker")
 * azure.webApps().inner().getByResourceGroup("devAppServiceAPIResourceGroup", "functionAppDocker").defaultHostName()
 *
 * azure.webApps().inner().getConfigurationSlot("devAppServiceResourceGroup", "dev-harness-docker", "dev")
 * azure.webApps().getByResourceGroup("devAppServiceResourceGroup", "dev-harness-docker").inner().enabledHostNames()
 * azure.webApps().getByResourceGroup("devAppServiceAPIResourceGroup",
 * "functionAppDocker").deploymentSlots().list().get(0).id()   --> slot id
 * azure.webApps().getByResourceGroup("devAppServiceAPIResourceGroup",
 * "functionAppDocker").deploymentSlots().getByName("stage").inner().outboundIpAddresses()  --> first ip address
 *
 * azure.webApps().inner().getInstanceInfo("devAppServiceAPIResourceGroup", "functionAppDocker",
 * "5419be2d080b475ceb9dcc9d903fad4461fc7e7438281274f93a37c3c0dfd08f")
 * azure.webApps().inner().listInstanceIdentifiers("devAppServiceAPIResourceGroup", "functionAppDocker")
 * azure.webApps().inner().listInstanceIdentifiers("devAppServiceAPIResourceGroup", "functionAppDocker").get(0)
 *
 * azure.virtualMachineScaleSets().getByResourceGroup("devVMSSResourceGroup",
 * "Azure__VMSS_azureService_prod__13").virtualMachines().list().get(0).listNetworkInterfaces().get(0).primaryIPConfiguration().inner()
 *
 * azure.activityLogs().defineQuery().startingFrom(DateTime.now().minusHours(1)).endsBefore(DateTime.now()).withAllPropertiesInResponse().filterByResource("/subscriptions/12d2db62-5aa9-471d-84bb-faa489b3e319/resourceGroups/anil-resourceGroup/providers/Microsoft.Web/sites/anil-webAppDocker").execute()
 * azureClient.webApps().list().get(0).streamDeploymentLogsAsync();
 *
 * azure.deployments().getByName("webappcreation4").deploymentOperations().list().get(0).targetResource().id() --> id of
 * resource created
 * azure.genericResources().deleteById("/subscriptions/12d2db62-5aa9-471d-84bb-faa489b3e319/resourceGroups/anil-arm-test/providers/Microsoft.Web/serverfarms/AppServicePlan-Test")
 *
 *
 * getAzureClient(getNewAzureConfig()).deployments().manager().inner().deploymentOperations().listAtManagementGroupScope("HarnessDev",
 * deploymentName)
 *
 * getAzureClient(getNewAzureConfig()).deployments().manager().inner().deployments().getAtScope("/providers/Microsoft.Management/managementGroups/HarnessDev",
 * deploymentName).properties() getAzureClient(getNewAzureConfig(),
 * "5aef6b46-7daa-45ea-a8d0-783aab69dea3").deployments().manager().inner().deployments().getAtSubscriptionScope("deployment-on-subscription_3").properties()
 * getAzureClient(getAzureConfig()).deployments().manager().inner().deployments().getAtScope("/subscriptions/12d2db62-5aa9-471d-84bb-faa489b3e319/resourceGroups/anil-arm-test/",
 * "test11").properties()
 */

public class AzureAppServicePoc extends AzureHelperService {
  private static final String CLIENT_ID = "baca1841-c593-407b-8793-98d3781f4fbf";
  private static final String TENANT_ID = "b229b2bb-5f33-4d22-bce0-730f6474e906";
  private static final String KEY = "3dH7aKJ/0rwGh+dx0YheGcUbh1dxYfrfTqTpKFcujd4=";
  private static final String SUBSCRIPTION_ID = "12d2db62-5aa9-471d-84bb-faa489b3e319";

  private static final String NEW_CLIENT_ID = "2fffcc83-09a8-450f-87f5-a25e3942dd10";
  private static final String NEW_KEY = "LcKHVgGfvwlKIx6Sm8_.1eb82i-a8-OJ9Z";
  private static final String CDP_NON_PROD_SUBSCRIPTION_ID = "5aef6b46-7daa-45ea-a8d0-783aab69dea3";

  public static void main(String[] args) {
    AzureAppServicePoc poc = new AzureAppServicePoc();
    //    poc.listWebApps();
    //    poc.listFunctionApps();
    //    poc.swapSlot("anil-resourceGroup", "anil-webAppDocker", "stage");
    //    poc.printStatus("anil-resourceGroup", "anil-webAppDocker", "test");
    //    poc.printSwapStatus("anil-resourceGroup", "anil-webAppDocker", "stage");
    //    poc.swapSlotCallback("anil-resourceGroup", "anil-webAppDocker", "stage");
    //    poc.doubleTest();
    poc.testARM();
  }

  private enum ARMScope { RESOURCE_GROUP, SUBSCRIPTION, MANAGEMENT_GROUP, TENANT }

  private static class ARMDeploymentContext {
    private final ARMScope scope;
    private final String resourceGroup;
    private String subscriptionId;
    private final String managementGroupId;
    private String tenantId;
    private final String deploymentName;

    ARMDeploymentContext(ARMScope scope, String resourceGroup, String subscriptionId, String managementGroupId,
        String tenantId, String deploymentName) {
      this.scope = scope;
      this.resourceGroup = resourceGroup;
      this.subscriptionId = subscriptionId;
      this.managementGroupId = managementGroupId;
      this.tenantId = tenantId;
      this.deploymentName = deploymentName;
    }
  }

  private void testARM() {
    //    testWhatIf();
    //    deployManagementGroupScope();
    //    deploySubscriptionScope();
    deployResourceGroupScope();
  }

  private void swapSlot(String resourceGroupName, String appName, String slotName) {
    Azure azureClient = getAzureClient(getAzureConfig(), SUBSCRIPTION_ID);
    //    DeploymentSlot deploymentSlot =
    //                azureClient.webApps().getByResourceGroup(resourceGroupName,
    //                appName).deploymentSlots().getByName(slotName);

    Completable completable = azureClient.webApps().getByResourceGroup(resourceGroupName, appName).swapAsync("stage");
    completable.subscribeOn(Schedulers.io()).observeOn(Schedulers.computation());

    completable.subscribe(new Subscriber<Object>() {
      @Override
      public void onCompleted() {
        System.out.println("Completed");
      }

      @Override
      public void onError(Throwable e) {
        System.out.println("Error");
      }

      @Override
      public void onNext(Object o) {
        System.out.println("next");
      }
    });

    //    Subscription subscribe = completable.toObservable().subscribe();
    //    subscribe.
    //    objectObservable.
    //    deploymentSlot.swapAsync("anil-webappdocker/stage");
    //    azureClient.webApps().getByResourceGroup(resourceGroupName, appName).swap("stage");
    System.out.println("Done");
  }

  private void swapSlotCallback(String resourceGroupName, String appName, String slotName) {
    //    CsmSlotEntity targetSlotSwapEntity = new CsmSlotEntity();
    //    targetSlotSwapEntity.withPreserveVnet(true);
    //    targetSlotSwapEntity.withTargetSlot("production");
    //
    SwapRestCallBack callBack = new SwapRestCallBack();
    Azure azureClient = getAzureClient(getAzureConfig(), SUBSCRIPTION_ID);
    //    WebApp byResourceGroup = azureClient.webApps().getByResourceGroup(resourceGroupName, appName);
    //    azureClient.webApps().getByResourceGroup().state()

    //    azureClient.webApps().inner().swapSlotSlotAsync(
    //        resourceGroupName, appName, slotName, targetSlotSwapEntity, callBack);

    //        DeploymentSlot deploymentSlot =
    //            azureClient.webApps().getByResourceGroup(resourceGroupName,
    //            appName).deploymentSlots().getByName(slotName);
    //    Completable completable = azureClient.webApps().getByResourceGroup(resourceGroupName,
    //    appName).swapAsync(slotName); Completable production = deploymentSlot.swapAsync(appName);

    //    DeploymentSlot byName = azureClient.webApps().getByResourceGroup(resourceGroupName, appName).swapAsync()
    //    Completable completable = byName.swapAsync("production");
    //    completable.subscribe();

    ExecutorService executorService = Executors.newFixedThreadPool(2);
    SlotSwapper slotSwapper = new SlotSwapper(resourceGroupName, appName, slotName, callBack);
    Future<?> slotSwapperFuture = executorService.submit(slotSwapper);

    DateTime startTime = DateTime.now().minusMinutes(1);
    pollSwapStatus(resourceGroupName, appName, slotName, callBack, startTime);

    try {
      executorService.shutdown();
      slotSwapperFuture.get();
    } catch (InterruptedException exception) {
      exception.printStackTrace();
    } catch (ExecutionException e) {
      e.printStackTrace();
    }
  }

  private void pollSwapStatus(
      String resourceGroupName, String appName, String slotName, SwapRestCallBack callBack, DateTime startTime) {
    Azure azureClient = getAzureClient(getAzureConfig(), SUBSCRIPTION_ID);
    WebApp webApp = azureClient.webApps().getByResourceGroup(resourceGroupName, appName);
    DeploymentSlot slot = webApp.deploymentSlots().getByName(slotName);
    String id = slot.id();
    int count = 0;
    boolean shouldPoll = true;

    while (shouldPoll) {
      DateTime endTime = DateTime.now();
      PagedList<EventData> eventData = azureClient.activityLogs()
                                           .defineQuery()
                                           .startingFrom(startTime)
                                           .endsBefore(endTime)
                                           .withAllPropertiesInResponse()
                                           .filterByResource(id)
                                           .execute();

      String message = eventData.stream()
                           .filter(ev -> ev != null && "SlotSwapJobProcessor".equals(ev.caller()))
                           .map(ev -> ev.operationName().localizedValue())
                           .collect(Collectors.joining(" :: "));

      System.out.println(String.format("Status Check - [%d] --> [%s]", ++count, message));
      shouldPoll = !(callBack.completed.get() && message.contains("Microsoft.Web/sites/slots/SlotSwap/action"));

      if (callBack.hasFailed()) {
        shouldPoll = false;
      }
      startTime = endTime;
      try {
        Thread.sleep(1000);
      } catch (InterruptedException exception) {
        exception.printStackTrace();
      }
    }
    System.out.println("Status checker is Done");
  }

  private class SlotSwapper implements Runnable {
    String resourceGroupName;
    String appName;
    String slotName;
    SwapRestCallBack callBack;

    SlotSwapper(String resourceGroupName, String appName, String slotName, SwapRestCallBack callBack) {
      this.resourceGroupName = resourceGroupName;
      this.appName = appName;
      this.slotName = slotName;
      this.callBack = callBack;
    }

    @Override
    public void run() {
      CsmSlotEntity targetSlotSwapEntity = new CsmSlotEntity();
      targetSlotSwapEntity.withPreserveVnet(true);
      targetSlotSwapEntity.withTargetSlot("production");

      Azure azureClient = getAzureClient(getAzureConfig(), SUBSCRIPTION_ID);
      azureClient.webApps().inner().swapSlotSlotAsync(
          resourceGroupName, appName, slotName, targetSlotSwapEntity, callBack);
      System.out.println("Slot swapper is Done");
    }
  }

  private class StatusChecker implements Runnable {
    String resourceGroupName;
    String appName;
    String slotName;
    SwapRestCallBack callBack;
    DateTime startTime;

    StatusChecker(
        String resourceGroupName, String appName, String slotName, SwapRestCallBack callBack, DateTime startTime) {
      this.resourceGroupName = resourceGroupName;
      this.appName = appName;
      this.slotName = slotName;
      this.callBack = callBack;
      this.startTime = startTime;
    }

    @Override
    public void run() {
      Azure azureClient = getAzureClient(getAzureConfig(), SUBSCRIPTION_ID);
      WebApp webApp = azureClient.webApps().getByResourceGroup(resourceGroupName, appName);
      DeploymentSlot slot = webApp.deploymentSlots().getByName(slotName);
      String id = slot.id();

      int count = 0;
      boolean shouldPoll = true;
      while (shouldPoll) {
        DateTime endTime = DateTime.now();
        PagedList<EventData> eventData = azureClient.activityLogs()
                                             .defineQuery()
                                             .startingFrom(startTime)
                                             .endsBefore(endTime)
                                             .withAllPropertiesInResponse()
                                             .filterByResource(id)
                                             .execute();

        String message = eventData.stream()
                             .filter(ev -> ev != null && "SlotSwapJobProcessor".equals(ev.caller()))
                             .map(ev -> ev.operationName().localizedValue())
                             .collect(Collectors.joining(" :: "));

        System.out.println(String.format("Status Check - [%d] --> [%s]", ++count, message));
        shouldPoll = !(callBack.completed.get() && message.contains("Microsoft.Web/sites/slots/SlotSwap/action"));

        if (callBack.hasFailed()) {
          shouldPoll = false;
        }

        try {
          Thread.sleep(15000);
        } catch (InterruptedException exception) {
          exception.printStackTrace();
        }
      }
      System.out.println("Status checker is Done");
    }
  }

  private void printSwapStatus(String resourceGroupName, String appName, String slotName) {
    Azure azureClient = getAzureClient(getAzureConfig(), SUBSCRIPTION_ID);
    WebApp webApp = azureClient.webApps().getByResourceGroup(resourceGroupName, appName);
    DeploymentSlot slot = webApp.deploymentSlots().getByName(slotName);
    String id = slot.id();
    DateTime startTime = DateTime.now().minusMinutes(1);
    int count = 1000;
    //    azureClient.webApps().getByResourceGroup("","").zipDeploy();
    //    azureClient.webApps().getByResourceGroup("","").warDeploy();
    while (count != 0) {
      try {
        Thread.sleep(5000);
      } catch (InterruptedException exception) {
        exception.printStackTrace();
      }

      DateTime endTime = DateTime.now();
      PagedList<EventData> eventData = azureClient.activityLogs()
                                           .defineQuery()
                                           .startingFrom(startTime)
                                           .endsBefore(endTime)
                                           .withAllPropertiesInResponse()
                                           .filterByResource(id)
                                           //              .filterByResourceGroup(resourceGroupName)
                                           .execute();

      //      String message = eventData.stream()
      //                           .map(ev
      //                               -> format(ACTIVITY_LOG_EVENT_DATA_TEMPLATE, ev.operationName().localizedValue(),
      //                                   ev.caller(), ev.status().localizedValue(), ev.description()))
      //                           .collect(Collectors.joining("\n"));
      //      System.out.println(String.format("Status Check - [%d] --> [%s]%n%n", count, message));

      String collect = eventData.stream()
                           .filter(ev -> ev != null && SLOT_SWAP_JOB_PROCESSOR_STR.equals(ev.caller()))
                           .map(ev -> ev.operationName().localizedValue())
                           .collect(Collectors.joining(" :: "));
      System.out.println(String.format("Status Check - [%d] --> [%s]%n", count, collect));

      //      startTime = endTime;
      count--;
    }
  }

  private void updateCommandScript() {
    Azure azureClient = getAzureClient(getAzureConfig(), SUBSCRIPTION_ID);
    SiteConfigResourceInner siteConfig =
        azureClient.webApps().inner().getConfiguration("resourceGroupName", "webAppName");
    siteConfig.withAppCommandLine("ssh web app");
    azureClient.webApps().inner().updateConfiguration("resourceGroupName", "webAppName", siteConfig);

    SiteConfigResourceInner siteConfigSlot =
        azureClient.webApps().inner().getConfigurationSlot("resourceGroupName", "webAppName", "slot");
    siteConfigSlot.withAppCommandLine("ssh slot");
    azureClient.webApps().inner().updateConfigurationSlot("resourceGroupName", "webAppName", "slot", siteConfigSlot);
  }

  private void printStatus(String resourceGroupName, String appName, String slotName) {
    Azure azureClient = getAzureClient(getAzureConfig(), SUBSCRIPTION_ID);

    while (true) {
      WebApp webApp = azureClient.webApps().getByResourceGroup(resourceGroupName, appName);
      //      ((WebAppImpl) getWebAppByName(context).get().update()).withStartUpCommand("test2").apply()

      SiteConfigResourceInner siteConfig =
          azureClient.webApps().inner().getConfiguration("resourceGroupName", "webAppName");
      siteConfig.withAppCommandLine("ssh web app");
      azureClient.webApps().inner().updateConfiguration("resourceGroupName", "webAppName", siteConfig);

      SiteConfigResourceInner siteConfigSlot =
          azureClient.webApps().inner().getConfigurationSlot("resourceGroupName", "webAppName", "slot");
      siteConfigSlot.withAppCommandLine("ssh slot");
      azureClient.webApps().inner().updateConfigurationSlot("resourceGroupName", "webAppName", "slot", siteConfigSlot);

      DeploymentSlot slot = webApp.deploymentSlots().getByName(slotName);
      String productionSlotState = webApp.state();
      String deploymentSlotState = slot.state();

      System.out.println("Production slot - " + productionSlotState);
      System.out.println("Deployment slot - " + deploymentSlotState);

      if (deploymentSlotState.equals("Stopped")) {
        break;
      }
      try {
        Thread.sleep(1000);
      } catch (InterruptedException exception) {
        exception.printStackTrace();
      }
    }
  }

  private void testWhatIf() {
    String deploymentName = "test8";
    String resourceGroup = "anil-arm-test";
    String contentVersion = "1.0.0.0";
    String templatePath = "https://raw.githubusercontent.com/anilchowdhury-harness/arm/main/demowebapp.json";
    String paramsPath = "https://raw.githubusercontent.com/anilchowdhury-harness/arm/main/demowebapp.Parameters.json";
    Azure azureClient = getAzureClient(getAzureConfig(), SUBSCRIPTION_ID);

    //    azureClient.subscriptions().getById().listLocations()
    //    azureClient.accessManagement().activeDirectoryGroups();

    DeploymentWhatIf parameters = new DeploymentWhatIf();
    DeploymentWhatIfProperties properties = new DeploymentWhatIfProperties();
    DeploymentWhatIfSettings settings = new DeploymentWhatIfSettings();
    settings.withResultFormat(FULL_RESOURCE_PAYLOADS);
    properties.withWhatIfSettings(settings);
    properties.withParametersLink(new ParametersLink().withUri(paramsPath).withContentVersion(contentVersion));
    properties.withTemplateLink(new TemplateLink().withUri(templatePath).withContentVersion(contentVersion));
    properties.withMode(DeploymentMode.COMPLETE);
    parameters.withProperties(properties);

    WhatIfOperationResultInner whatIfOperationResultInner =
        azureClient.deployments().manager().inner().deployments().whatIf(resourceGroup, deploymentName, parameters);
    if (whatIfOperationResultInner != null) {
      whatIfOperationResultInner.changes().forEach(change -> {
        String resourceId = change.resourceId();
        String changeType = change.changeType().toString();
        System.out.println(String.format("%s - %s", resourceId, changeType));
        if ("Modify".equalsIgnoreCase(changeType)) {
          change.delta().forEach(d -> {
            String propertyChangeType = d.propertyChangeType().toString();
            String path = d.path();
            System.out.println(String.format("%s - %s", propertyChangeType, path));
          });
        }
        //      change.delta().toString()
      });
    }
  }

  private void deploySubscriptionScope() {
    String deploymentName = "anil-subscriptionScope-1";
    String contentVersion = "1.0.0.0";
    String templatePath =
        "https://raw.githubusercontent.com/anilchowdhury-harness/arm/main/subscriptionScope/demoSubscription.json";
    String paramsPath =
        "https://raw.githubusercontent.com/anilchowdhury-harness/arm/main/subscriptionScope/demoSubscription.Parameters.json";
    Azure azureClient = getAzureClient(getNewAzureConfig(), CDP_NON_PROD_SUBSCRIPTION_ID);

    DeploymentProperties prop = new DeploymentProperties();
    prop.withParametersLink(new ParametersLink().withUri(paramsPath).withContentVersion(contentVersion));
    prop.withTemplateLink(new TemplateLink().withUri(templatePath).withContentVersion(contentVersion));
    prop.withMode(DeploymentMode.INCREMENTAL);

    DeploymentInner deploymentInner = new DeploymentInner();
    deploymentInner.withProperties(prop);
    deploymentInner.withLocation("West US");

    try {
      DeploymentsInner deployments = azureClient.deployments().manager().inner().deployments();
      deployments.beginCreateOrUpdateAtSubscriptionScope(deploymentName, deploymentInner);
      ARMDeploymentContext context =
          new ARMDeploymentContext(ARMScope.SUBSCRIPTION, "", CDP_NON_PROD_SUBSCRIPTION_ID, "", "", deploymentName);
      steadyStateCheck(context, azureClient);
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
  }

  private void deployManagementGroupScope() {
    String deploymentName = "anil-managementTest6";
    String managementGroupId = "HarnessARMTest";
    String contentVersion = "1.0.0.0";
    String templatePath =
        "https://raw.githubusercontent.com/anilchowdhury-harness/arm/main/managementGroup/policy/managementWithPolicy.json";
    String paramsPath =
        "https://raw.githubusercontent.com/anilchowdhury-harness/arm/main/managementGroup/policy/managementWithPolicy.Parameters.json";
    Azure azureClient = getAzureClient(getNewAzureConfig());

    DeploymentProperties prop = new DeploymentProperties();
    prop.withParametersLink(new ParametersLink().withUri(paramsPath).withContentVersion(contentVersion));
    prop.withTemplateLink(new TemplateLink().withUri(templatePath).withContentVersion(contentVersion));
    prop.withMode(DeploymentMode.INCREMENTAL);

    DeploymentInner deploymentInner = new DeploymentInner();
    deploymentInner.withProperties(prop);
    deploymentInner.withLocation("East US");

    try {
      DeploymentsInner deployments = azureClient.deployments().manager().inner().deployments();
      deployments.beginCreateOrUpdateAtManagementGroupScope(managementGroupId, deploymentName, deploymentInner);
      ARMDeploymentContext context =
          new ARMDeploymentContext(ARMScope.MANAGEMENT_GROUP, "", "", managementGroupId, "", deploymentName);
      steadyStateCheck(context, azureClient);
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
  }

  private void deployResourceGroupScope() {
    String deploymentName = "array-test";
    String resourceGroup = "anil-arm-test";
    String contentVersion = "1.0.0.0";
    //    String templatePath =
    //    "https://raw.githubusercontent.com/anilchowdhury-harness/arm/main/resourceGroup/demowebapp.json";
    //        String templatePath =
    //            "https://raw.githubusercontent.com/anilchowdhury-harness/arm/main/resourceGroup/demowebappWithOutOutPuts.json";
    //    String templatePath =
    //        "https://raw.githubusercontent.com/anilchowdhury-harness/arm/main/exportedTemplateWithIncludeParametersDefault.json";
    String templatePath =
        "https://raw.githubusercontent.com/anilchowdhury-harness/arm/main/resourceGroup/armWithArray.json";
    String paramsPath =
        "https://raw.githubusercontent.com/anilchowdhury-harness/arm/main/resourceGroup/armWithArray.Parameters.json";

    Azure azureClient = getAzureClient(getAzureConfig(), SUBSCRIPTION_ID);

    DeploymentProperties prop = new DeploymentProperties();
    prop.withParametersLink(new ParametersLink().withUri(paramsPath).withContentVersion(contentVersion));
    prop.withTemplateLink(new TemplateLink().withUri(templatePath).withContentVersion(contentVersion));
    prop.withMode(DeploymentMode.INCREMENTAL);

    DeploymentInner deploymentInner = new DeploymentInner();
    deploymentInner.withProperties(prop);

    try {
      DeploymentsInner deployments = azureClient.deployments().manager().inner().deployments();
      deployments.beginCreateOrUpdate(resourceGroup, deploymentName, deploymentInner);
      // deployments.createOrUpdate(resourceGroup, deploymentName, deploymentInner); //blocking call
      ARMDeploymentContext context =
          new ARMDeploymentContext(ARMScope.RESOURCE_GROUP, resourceGroup, "", "", "", deploymentName);
      steadyStateCheck(context, azureClient);
      //      System.out.println(azureClient.resourceGroups()
      //                             .getByName(resourceGroup)
      //                             .exportTemplate(ResourceGroupExportTemplateOptions.INCLUDE_PARAMETER_DEFAULT_VALUE)
      //                             .templateJson());
    } catch (Exception exception) {
      System.out.println(exception.getMessage());
    }
  }

  private void testArm() {
    Azure azureClient = getAzureClient(getAzureConfig(), SUBSCRIPTION_ID);

    String contentVersion = "1.0.0.0";
    String templatePath = "https://raw.githubusercontent.com/anilchowdhury-harness/arm/main/demowebapp.json";
    String paramsPath = "https://raw.githubusercontent.com/anilchowdhury-harness/arm/main/demowebapp.Parameters.json";

    try {
      String deploymentName = "test40";
      String resourceGroup = "anil-arm-test";
      azureClient.deployments()
          .define(deploymentName)
          .withExistingResourceGroup(resourceGroup)
          .withTemplateLink(templatePath, contentVersion)
          .withParametersLink(paramsPath, contentVersion)
          .withMode(DeploymentMode.INCREMENTAL)
          .beginCreate();
      //          .create(); //blocking call
      ARMDeploymentContext context =
          new ARMDeploymentContext(ARMScope.RESOURCE_GROUP, resourceGroup, "", "", "", deploymentName);
      steadyStateCheck(context, azureClient);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void steadyStateCheck(ARMDeploymentContext context, Azure azureClient) throws InterruptedException {
    String provisioningState = getProvisionState(context, azureClient);

    while (!isDeploymentComplete(provisioningState)) {
      System.out.println(
          String.format("%nDeployment Status for - [%s] is [%s]", context.deploymentName, provisioningState));
      PagedList<DeploymentOperationInner> deploymentOperations = getDeploymentOperations(context, azureClient);
      deploymentOperations.forEach(this::printDeploymentOperationsInner);

      Thread.sleep(1000);
      provisioningState = getProvisionState(context, azureClient);
    }
    System.out.println(String.format(
        "%nDeployment - [%s] is completed with status - [%s]", context.deploymentName, provisioningState));
    System.out.println(
        String.format("Deployment Outputs - [%s] ", JsonUtils.prettifyJsonString(getOutputs(azureClient, context))));
  }

  private String getOutputs(Azure azureClient, ARMDeploymentContext context) {
    DeploymentsInner deployments = azureClient.deployments().manager().inner().deployments();
    DeploymentExtendedInner extendedInner;
    switch (context.scope) {
      case RESOURCE_GROUP:
        extendedInner = deployments.getByResourceGroup(context.resourceGroup, context.deploymentName);
        break;
      case SUBSCRIPTION:
        extendedInner = deployments.getAtSubscriptionScope(context.deploymentName);
        break;
      case MANAGEMENT_GROUP:
        extendedInner = deployments.getAtManagementGroupScope(context.managementGroupId, context.deploymentName);
        break;
      case TENANT:
        extendedInner = deployments.getAtTenantScope(context.deploymentName);
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + context.scope);
    }
    Object outputs = extendedInner.properties().outputs();
    String jsonOutput = JsonUtils.asJson(outputs);
    saveARMOutputs(jsonOutput);
    return jsonOutput;
  }

  private void saveARMOutputs(String jsonOutput) {
    Map<String, Object> outputMap = new LinkedHashMap<>();
    try {
      TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {};
      Map<String, Object> json = new ObjectMapper().readValue(IOUtils.toInputStream(jsonOutput), typeRef);

      json.forEach((key, object) -> outputMap.put(key, ((Map<String, Object>) object).get("value")));
    } catch (IOException ignored) {
    }
    //    System.out.println(outputMap.toString());
  }

  private boolean isDeploymentComplete(String provisioningState) {
    return (provisioningState == null) || "Succeeded".equalsIgnoreCase(provisioningState)
        || "Failed".equalsIgnoreCase(provisioningState) || "Cancelled".equalsIgnoreCase(provisioningState);
  }

  private void steadyStateCheck(ARMDeploymentContext context, Azure azureClient, String resourceGroup,
      String deploymentName) throws InterruptedException {
    Deployment deployment = azureClient.deployments().getByResourceGroup(context.resourceGroup, context.deploymentName);
    while (!deployment.provisioningState().equalsIgnoreCase("Succeeded")) {
      System.out.println(
          String.format("%nDeployment Status for - [%s] is [%s]", deploymentName, deployment.provisioningState()));
      PagedList<DeploymentOperation> deploymentOperations = deployment.deploymentOperations().list();

      deploymentOperations.forEach(this::printDeploymentOperations);

      Thread.sleep(1000);
      deployment = azureClient.deployments().getByResourceGroup(resourceGroup, deploymentName);
    }
  }

  private String getProvisionState(ARMDeploymentContext context, Azure azureClient) {
    DeploymentsInner deployments = azureClient.deployments().manager().inner().deployments();
    DeploymentExtendedInner extendedInner;
    switch (context.scope) {
      case RESOURCE_GROUP:
        extendedInner = deployments.getByResourceGroup(context.resourceGroup, context.deploymentName);
        break;
      case SUBSCRIPTION:
        extendedInner = deployments.getAtSubscriptionScope(context.deploymentName);
        break;
      case MANAGEMENT_GROUP:
        extendedInner = deployments.getAtManagementGroupScope(context.managementGroupId, context.deploymentName);
        break;
      case TENANT:
        extendedInner = deployments.getAtTenantScope(context.deploymentName);
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + context.scope);
    }
    return extendedInner.properties().provisioningState();
  }

  private PagedList<DeploymentOperationInner> getDeploymentOperations(ARMDeploymentContext context, Azure azureClient) {
    DeploymentOperationsInner deploymentOperationsInner =
        azureClient.deployments().manager().inner().deploymentOperations();
    PagedList<DeploymentOperationInner> operationInners;
    switch (context.scope) {
      case RESOURCE_GROUP:
        operationInners = deploymentOperationsInner.listByResourceGroup(context.resourceGroup, context.deploymentName);
        break;
      case SUBSCRIPTION:
        operationInners = deploymentOperationsInner.listAtSubscriptionScope(context.deploymentName);
        break;
      case MANAGEMENT_GROUP:
        operationInners =
            deploymentOperationsInner.listAtManagementGroupScope(context.managementGroupId, context.deploymentName);
        break;
      case TENANT:
        operationInners = deploymentOperationsInner.listAtTenantScope(context.deploymentName);
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + context.scope);
    }
    return operationInners;
  }

  private void printDeploymentOperations(DeploymentOperation operation) {
    if (operation.targetResource() != null) {
      System.out.println(String.format("%s - %s: %s %s", operation.targetResource().resourceType(),
          operation.targetResource().resourceName(), operation.provisioningState(),
          operation.statusMessage() != null ? operation.statusMessage() : ""));
    }
  }

  private void printDeploymentOperationsInner(DeploymentOperationInner operation) {
    if (operation.properties() != null && operation.properties().targetResource() != null) {
      DeploymentOperationProperties properties = operation.properties();
      System.out.println(String.format("%s - %s: [%s] %s", properties.targetResource().resourceType(),
          properties.targetResource().resourceName(), properties.provisioningState(),
          properties.statusMessage() != null ? properties.statusMessage() : ""));
    }
  }

  //  private AzureResourceManager getAzureResourceManger() {
  //    final AzureProfile profile = new AzureProfile(AZURE);
  //    final TokenCredential credential = new DefaultAzureCredentialBuilder()
  //                                           .authorityHost(profile.getEnvironment().getActiveDirectoryEndpoint())
  //                                           .build();
  //
  //    return AzureResourceManager.configure()
  //        .withLogLevel(HttpLogDetailLevel.BASIC)
  //        .authenticate(credential, profile)
  //        .withTenantId(TENANT_ID)
  //        .withSubscription(SUBSCRIPTION_ID);

  //  azure.genericResources().deleteByIdAsync("resourceid", new ServiceCallback<Void>() {
  //      @Override
  //      public void failure(Throwable throwable) {
  //
  //      }
  //
  //      @Override
  //      public void success(Void aVoid) {
  //
  //      }
  //    });

  //  getAzureClient(getAzureConfig(), SUBSCRIPTION_ID)
  //      .deployments()
  //      .define("")
  //      .withExistingResourceGroup("")
  //      .withTemplate("")
  //      .withParameters("")
  //      .withMode()
  //      .createAsync();

  //  }

  private void createIdToNameMap() {
    Azure azureClient = getAzureClient(getAzureConfig(), SUBSCRIPTION_ID);
    VirtualMachineScaleSet vmss =
        azureClient.virtualMachineScaleSets().getByResourceGroup("devVMSSResourceGroup", "doc__canary__3");
    Map<String, String> collect = vmss.virtualMachines().list().stream().collect(
        Collectors.toMap(VirtualMachineScaleSetVM::instanceId, vm -> vm.name()));
    System.out.println(collect);
  }

  private void listInstanceCountForAllVMSS() {
    Azure azureClient = getAzureClient(getAzureConfig(), SUBSCRIPTION_ID);
    PagedList<VirtualMachineScaleSet> list = azureClient.virtualMachineScaleSets().list();
    int totalScaleSet = 0;
    int totalVMInstances = 0;
    while (!list.isEmpty()) {
      for (VirtualMachineScaleSet virtualMachineScaleSet : list) {
        int capacity = virtualMachineScaleSet.capacity();
        System.out.println(String.format("VMSS: [%s] has [%d] instances", virtualMachineScaleSet.name(), capacity));
        totalScaleSet++;
        totalVMInstances += capacity;
      }
      list.clear();
      if (list.hasNextPage()) {
        list.loadNextPage();
      }
    }
    System.out.println(String.format("Total scale set count = [%d]", totalScaleSet));
    System.out.println(String.format("Total virtual machine count = [%d]", totalVMInstances));
  }

  private void listWebApps() {
    // azure.webApps().inner().list()  --> list all webapp, api app, function app

    Azure azure = getAzureClient(getAzureConfig(), SUBSCRIPTION_ID);
    WebApps webApps = azure.appServices().webApps();
    PagedList<WebApp> webAppList = webApps.list();
    //    azure.deployments().deleteById();
    for (WebApp webApp : webAppList) {
      System.out.println(webApp.name());
    }
    //    azure.activityLogs().
  }

  private void listFunctionApps() {
    Azure azure = getAzureClient(getAzureConfig(), SUBSCRIPTION_ID);
    FunctionApps functionApps = azure.appServices().functionApps();

    PagedList<FunctionApp> functionAppsList = functionApps.list();
    for (FunctionApp functionApp : functionAppsList) {
      System.out.println(functionApp.name());
    }
  }

  // Creating new web app
  private void createNewWebApp(WebApps webApps) {
    webApps.define("")
        .withExistingLinuxPlan(null)
        .withExistingResourceGroup("")
        .withBuiltInImage(null)
        .withJavaVersion(null)
        .withWebContainer(null)
        .create();
  }

  private void exploreApis(Azure azure) {
    // Getting web app by id
    WebApp webApp = azure.webApps().getById("");

    // deploying an artifact to production slot
    webApp.warDeploy(new File(""));
    // Listing slots

    DeploymentSlots deploymentSlots = webApp.deploymentSlots();
    deploymentSlots.list();

    // Getting deployment slot by name
    deploymentSlots.getByName("");
    deploymentSlots.define(""); // create new slot

    // deploying to a specific slot
    deploymentSlots.getByName("").warDeploy(new File(""));

    // swapping slot
    webApp.swapAsync("");
    webApp.applySlotConfigurations("");

    // site config
    SiteConfig siteConfig = webApp.inner().siteConfig();

    // getting traffic weight of a slot
    List<RampUpRule> getRampUpRules = siteConfig.experiments().rampUpRules();
    getRampUpRules.get(0).reroutePercentage();

    // updating traffic weight of a slot
    Experiments experiments = siteConfig.experiments();
    RampUpRule rampUpRule = experiments.rampUpRules().get(0);
    rampUpRule.withReroutePercentage(20.0);
    webApp.update();

    // app settings
    webApp.getAppSettings();
    // updating app settings
    webApp.update().withAppSetting("key", "value");

    // create web app with private docker registry
    getAzureClient(getAzureConfig(), SUBSCRIPTION_ID)
        .webApps()
        .define("")
        .withExistingLinuxPlan(null)
        .withExistingResourceGroup("")
        .withPrivateRegistryImage("", "")
        .withCredentials("", "")
        .create();

    // updating existing web app with docker image
    webApp.update().withPrivateRegistryImage("", "").withCredentials("", "").withAppSettings(null).applyAsync();
  }

  private AzureConfig getAzureConfig() {
    return AzureConfig.builder().tenantId(TENANT_ID).clientId(CLIENT_ID).key(KEY.toCharArray()).build();
  }

  private AzureConfig getNewAzureConfig() {
    return AzureConfig.builder().tenantId(TENANT_ID).clientId(NEW_CLIENT_ID).key(NEW_KEY.toCharArray()).build();
  }

  private static class SwapRestCallBack implements ServiceCallback<Void> {
    AtomicBoolean failed = new AtomicBoolean(false);
    AtomicBoolean completed = new AtomicBoolean(false);

    @Override
    public void failure(Throwable throwable) {
      failed.set(true);
      completed.set(true);
      System.out.println(throwable.getMessage());
    }

    @Override
    public void success(Void aVoid) {
      System.out.println("Swap status is success");
      completed.set(true);
    }

    public boolean hasFailed() {
      return failed.get();
    }
  }
}
