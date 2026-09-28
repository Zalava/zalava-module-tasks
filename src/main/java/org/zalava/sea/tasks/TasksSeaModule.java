package org.zalava.tasks;

import org.zalava.InvocationContext;
import org.zalava.ModuleDescriptor;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.ProviderFactory;
import org.zalava.ProviderFactoryContext;
import org.zalava.ProviderFactoryDescriptor;
import org.zalava.SeaModule;
import org.zalava.SeaOperationResult;
import org.zalava.SeaProvider;
import org.zalava.SeaToolDescriptor;
import org.zalava.SeaToolInputSchemas;
import org.zalava.tasks.TaskService;
import tools.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class TasksSeaModule implements SeaModule {
    static final String ID = "zalava-module-tasks";
    static final String VERSION = moduleVersion();
    @Override public ModuleDescriptor descriptor() { return new ModuleDescriptor(ID, VERSION, "Tasks", "Host-owned task operations through SEA."); }
    @Override public List<ProviderFactory> providerFactories() { return List.of(new Factory()); }
    static final class Factory implements ProviderFactory {
        @Override public ProviderFactoryDescriptor descriptor() { return new ProviderFactoryDescriptor("tasks", ID, "tasks", "Tasks", "Creates the host-owned tasks provider."); }
        @Override public List<SeaProvider> createProviders(ProviderFactoryContext context) {
            return context.service(TaskService.class).<List<SeaProvider>>map(service -> List.of(new Provider(service))).orElseGet(List::of);
        }
    }
    static final class Provider implements SeaProvider {
        private final TaskService tasks;
        private final ProviderDescriptor descriptor = new ProviderDescriptor("tasks", ID, "tasks", "Tasks", "Host-owned task operations.", VERSION, new ProviderCapabilities(true,false,false,false,false,false,false,false), List.of("tasks"), Map.of());
        Provider(TaskService tasks) { this.tasks = tasks; }
        @Override public ProviderDescriptor descriptor() { return descriptor; }
        @Override public ProviderCapabilities capabilities() { return descriptor.capabilities(); }
        @Override public List<SeaToolDescriptor> listTools() { return List.of(
                tool("createTask", "Create a host-owned task.", true), tool("scheduleTask", "Schedule a host-owned task.", true),
                tool("scheduleRecurringTask", "Schedule a recurring host-owned task.", true), tool("deleteRecurringTask", "Delete a recurring host-owned task.", true),
                tool("listRecurringTasks", "List recurring host-owned tasks.", false)); }
        @Override public SeaOperationResult callTool(String name, JsonNode arguments, InvocationContext context) {
            requireObject(arguments);
            return switch(name) {
                case "createTask" -> SeaOperationResult.success(tasks.create(context, text(arguments,"name"), text(arguments,"description")));
                case "scheduleTask" -> SeaOperationResult.success(tasks.schedule(context, text(arguments,"executionTime"), text(arguments,"name"), text(arguments,"description")));
                case "scheduleRecurringTask" -> SeaOperationResult.success(tasks.scheduleRecurring(context, text(arguments,"cronExpression"), text(arguments,"name"), text(arguments,"description")));
                case "deleteRecurringTask" -> SeaOperationResult.success(tasks.deleteRecurring(context, text(arguments,"name")));
                case "listRecurringTasks" -> SeaOperationResult.success(tasks.listRecurring(context));
                default -> throw new UnsupportedOperationException("Unknown task tool: " + name);
            };
        }
        private static SeaToolDescriptor tool(String name, String description, boolean sideEffecting) {
            return new SeaToolDescriptor(name, description, sideEffecting, List.of("tasks"), schema(name));
        }
        private static Map<String, Object> schema(String name) {
            return switch (name) {
                case "createTask" -> SeaToolInputSchemas.object(Map.of("name", SeaToolInputSchemas.string(), "description", SeaToolInputSchemas.string()), "name", "description");
                case "scheduleTask" -> SeaToolInputSchemas.object(Map.of("executionTime", SeaToolInputSchemas.string(), "name", SeaToolInputSchemas.string(), "description", SeaToolInputSchemas.string()), "executionTime", "name", "description");
                case "scheduleRecurringTask" -> SeaToolInputSchemas.object(Map.of("cronExpression", SeaToolInputSchemas.string(), "name", SeaToolInputSchemas.string(), "description", SeaToolInputSchemas.string()), "cronExpression", "name", "description");
                case "deleteRecurringTask" -> SeaToolInputSchemas.object(Map.of("name", SeaToolInputSchemas.string()), "name");
                case "listRecurringTasks" -> SeaToolInputSchemas.object(Map.of());
                default -> throw new IllegalArgumentException("Unknown task tool: " + name);
            };
        }
        private static void requireObject(JsonNode arguments) { if (arguments == null || !arguments.isObject()) throw new IllegalArgumentException("arguments must be an object"); }
        private static String text(JsonNode arguments, String name) { if (!arguments.hasNonNull(name) || arguments.get(name).asString().isBlank()) throw new IllegalArgumentException(name + " is required"); return arguments.get(name).asString(); }
    }
    private static String moduleVersion() {
        try (var stream = TasksSeaModule.class.getResourceAsStream("/module.properties")) {
            if (stream == null) throw new IllegalStateException("Missing module.properties");
            Properties properties = new Properties();
            properties.load(stream);
            String version = properties.getProperty("module.version");
            if (version == null || version.isBlank()) throw new IllegalStateException("Missing module.version");
            return version;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read module.properties", exception);
        }
    }
}
