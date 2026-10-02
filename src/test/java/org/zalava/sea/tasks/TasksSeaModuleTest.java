package org.zalava.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalava.InvocationContext;
import org.zalava.ZalavaOperationResult;
import org.zalava.ZalavaProvider;
import org.zalava.ZalavaToolDescriptor;
import org.zalava.testing.ConfigFixture;
import org.zalava.testing.ModuleContractKit;
import org.zalava.testing.ProviderFixture;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Exercises the real built module JAR at the stable {@code module-api} boundary through the
 * released contract kit. Host-owned task persistence, scheduling and policy stay covered by SEA.
 */
class TasksSeaModuleTest {

  private static final String MODULE_ID = "zalava-module-tasks";
  private static final String PROVIDER_ID = "tasks";

  private ModuleContractKit kit;

  @BeforeEach
  void loadTheBuiltArtifact() {
    Path artifact = Path.of(System.getProperty("module.artifact"));
    String version = System.getProperty("module.version");
    kit = ModuleContractKit.load(artifact, List.of(), MODULE_ID, version);
  }

  @AfterEach
  void closeTheArtifact() throws Exception {
    if (kit != null) {
      kit.close();
    }
  }

  @Test
  void loadsTheModuleFromTheBuiltArtifact() {
    assertThat(kit.module().getClass().getClassLoader()).isNotSameAs(getClass().getClassLoader());
    assertThat(
            kit.module().getClass().getProtectionDomain().getCodeSource().getLocation().toString())
        .endsWith(".jar");
  }

  @Test
  void exposesTheModuleOwnedDescriptorAndConfigurationContract() {
    assertThat(kit.moduleId()).isEqualTo(MODULE_ID);
    assertThat(kit.version()).isEqualTo(System.getProperty("module.version"));
    assertThat(kit.module().descriptor().displayName()).isEqualTo("Tasks");

    Map<String, Object> schema = kit.module().configuration().jsonSchema();
    assertThat(schema).containsEntry("type", "object");
    assertThat(schema.get("properties")).isInstanceOf(Map.class);
    assertThat((Map<?, ?>) schema.get("properties")).isEmpty();
  }

  @Test
  void createsNoProviderUntilTheHostSuppliesTheScopedTaskService() {
    try (ProviderFixture providers = kit.providers(ConfigFixture.empty())) {
      assertThat(providers.providers()).isEmpty();
    }
  }

  @Test
  void createsTheConfiguredProviderAndDeclaresItsTools() {
    try (ProviderFixture providers = kit.providers(hostService(new CapturingTasks()))) {
      ZalavaProvider provider = providers.requireProvider(PROVIDER_ID);

      assertThat(provider.descriptor().moduleId()).isEqualTo(MODULE_ID);
      assertThat(provider.descriptor().providerType()).isEqualTo("tasks");
      assertThat(provider.listTools().stream().map(ZalavaToolDescriptor::name))
          .containsExactly(
              "createTask",
              "scheduleTask",
              "scheduleRecurringTask",
              "deleteRecurringTask",
              "listRecurringTasks");
      assertThat(provider.listTools().stream().map(ZalavaToolDescriptor::sideEffecting))
          .containsExactly(true, true, true, true, false);
    }
  }

  @Test
  void declaresTheModuleOwnedToolInputSchemas() {
    try (ProviderFixture providers = kit.providers(hostService(new CapturingTasks()))) {
      ZalavaToolDescriptor create = providers.requireTool(PROVIDER_ID, "createTask");
      assertThat(create.inputSchema())
          .containsEntry("type", "object")
          .containsEntry("additionalProperties", false);
      assertThat(create.inputSchema().get("required")).isEqualTo(List.of("name", "description"));
      assertThat(
              providers
                  .requireTool(PROVIDER_ID, "listRecurringTasks")
                  .inputSchema()
                  .get("required"))
          .isEqualTo(List.of());
    }
  }

  @Test
  void delegatesScopedTaskOperationsToTheHostService() {
    CapturingTasks tasks = new CapturingTasks();
    try (ProviderFixture providers = kit.providers(hostService(tasks))) {
      ZalavaOperationResult created =
          providers.invoke(
              PROVIDER_ID,
              "createTask",
              arguments().put("name", "report").put("description", "Write report"),
              new InvocationContext("alice", false, Map.of()));
      assertThat(created.success()).isTrue();
      assertThat(tasks.createdActorId).isEqualTo("alice");
      assertThat(tasks.createdName).isEqualTo("report");
      assertThat(tasks.createdDescription).isEqualTo("Write report");

      providers.invoke(
          PROVIDER_ID,
          "scheduleTask",
          arguments()
              .put("executionTime", "2026-08-20T09:00:00")
              .put("name", "report")
              .put("description", "Write report"));
      assertThat(tasks.scheduledTime).isEqualTo("2026-08-20T09:00:00");
      assertThat(tasks.scheduledName).isEqualTo("report");

      providers.invoke(
          PROVIDER_ID,
          "scheduleRecurringTask",
          arguments()
              .put("cronExpression", "0 9 * * *")
              .put("name", "daily")
              .put("description", "Daily report"));
      assertThat(tasks.recurringCron).isEqualTo("0 9 * * *");

      providers.invoke(PROVIDER_ID, "deleteRecurringTask", arguments().put("name", "daily"));
      assertThat(tasks.deletedName).isEqualTo("daily");
    }
  }

  @Test
  void returnsTheHostOwnedRecurringProjection() {
    try (ProviderFixture providers = kit.providers(hostService(new CapturingTasks()))) {
      ZalavaOperationResult result =
          providers.invoke(PROVIDER_ID, "listRecurringTasks", arguments());
      assertThat(result.content())
          .isEqualTo(List.of(new RecurringTaskSummary("daily", "daily", "Daily report")));
    }
  }

  @Test
  void validatesObjectAndRequiredArguments() {
    try (ProviderFixture providers = kit.providers(hostService(new CapturingTasks()))) {
      assertThatThrownBy(() -> providers.invoke(PROVIDER_ID, "createTask", array()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("arguments must be an object");
      assertThatThrownBy(() -> providers.invoke(PROVIDER_ID, "deleteRecurringTask", arguments()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("name is required");
    }
  }

  private static ConfigFixture hostService(TaskService service) {
    return ConfigFixture.empty().hostService(MODULE_ID, TaskService.class, service);
  }

  @Test
  void rejectsNullBlankAndExplicitNullInputsAndUnknownOperations() {
    CapturingTasks tasks = new CapturingTasks();
    try (ProviderFixture providers = kit.providers(hostService(tasks))) {
      ZalavaProvider provider = providers.requireProvider(PROVIDER_ID);
      assertThat(provider.capabilities().supportsTools()).isTrue();
      assertThatThrownBy(() -> provider.callTool("createTask", null, InvocationContext.system()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("arguments must be an object");
      assertThatThrownBy(
              () ->
                  providers.invoke(PROVIDER_ID, "deleteRecurringTask", arguments().putNull("name")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("name is required");
      assertThatThrownBy(
              () ->
                  providers.invoke(
                      PROVIDER_ID, "deleteRecurringTask", arguments().put("name", " ")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("name is required");
      assertThatThrownBy(
              () -> provider.callTool("unknown", arguments(), InvocationContext.system()))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("Unknown task tool");
      assertThat(tasks.deletedName).isNull();
    }
  }

  private static ObjectNode arguments() {
    return JsonNodeFactory.instance.objectNode();
  }

  private static JsonNode array() {
    return JsonNodeFactory.instance.arrayNode();
  }

  private static final class CapturingTasks implements TaskService {
    String createdActorId;
    String createdName;
    String createdDescription;
    String scheduledTime;
    String scheduledName;
    String recurringCron;
    String deletedName;

    @Override
    public TaskServiceResult create(InvocationContext context, String name, String description) {
      createdActorId = context.actorId();
      createdName = name;
      createdDescription = description;
      return TaskServiceResult.created(new TaskReference("task-created"));
    }

    @Override
    public TaskServiceResult schedule(
        InvocationContext context, String executionTime, String name, String description) {
      scheduledTime = executionTime;
      scheduledName = name;
      return TaskServiceResult.scheduled(new TaskReference("task-scheduled"));
    }

    @Override
    public TaskServiceResult scheduleRecurring(
        InvocationContext context, String cronExpression, String name, String description) {
      recurringCron = cronExpression;
      return TaskServiceResult.recurringScheduled(
          new RecurringTaskSummary("daily", name, description));
    }

    @Override
    public TaskServiceResult deleteRecurring(InvocationContext context, String recurringTaskName) {
      deletedName = recurringTaskName;
      return TaskServiceResult.recurringDeleted(
          new RecurringTaskSummary("daily", recurringTaskName, ""));
    }

    @Override
    public List<RecurringTaskSummary> listRecurring(InvocationContext context) {
      return List.of(new RecurringTaskSummary("daily", "daily", "Daily report"));
    }
  }
}
