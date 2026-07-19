package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:sockets/ip-name-lookup" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface IpNameLookupContext extends WasmComponentContext {
    String INTERFACE = "wasi:sockets/ip-name-lookup";

    @Override
    default String name() {
        return "ip-name-lookup";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "resolve-addresses", this::ipNameLookupResolveAddressesImpl),
                new ComponentImportFunction(versioned(), "[method]resolve-address-stream.resolve-next-address", this::resolveAddressStreamResolveNextAddressImpl),
                new ComponentImportFunction(versioned(), "[method]resolve-address-stream.subscribe", this::resolveAddressStreamSubscribeImpl)
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of(
                new ComponentImportResource(versioned(), "resolve-address-stream", this::dropResolveAddressStream),
                new ComponentImportResource(versioned(), "network", this::dropNetwork),
                new ComponentImportResource(versioned(), "pollable", this::dropPollable)
            );
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    WitResult ipNameLookupResolveAddresses(WasmtimeComponentInstance instance, WitResource network, String name);

    WitResult resolveAddressStreamResolveNextAddress(WasmtimeComponentInstance instance, WitResource self);

    WitResource resolveAddressStreamSubscribe(WasmtimeComponentInstance instance, WitResource self);

    void dropResolveAddressStream(int rep);

    void dropNetwork(int rep);

    void dropPollable(int rep);

    private Object[] ipNameLookupResolveAddressesImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource network = (WitResource) args[0];
        String name = (String) args[1];
        return new Object[] { ipNameLookupResolveAddresses(instance, network, name) };
    }

    private Object[] resolveAddressStreamResolveNextAddressImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { resolveAddressStreamResolveNextAddress(instance, self) };
    }

    private Object[] resolveAddressStreamSubscribeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { resolveAddressStreamSubscribe(instance, self) };
    }

}
