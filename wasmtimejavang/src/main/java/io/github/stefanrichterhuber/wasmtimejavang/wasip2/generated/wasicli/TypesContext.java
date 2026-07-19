package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitEnum;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitVariant;
import java.util.Optional;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:filesystem/types" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface TypesContext extends WasmComponentContext {
    String INTERFACE = "wasi:filesystem/types";

    @Override
    default String name() {
        return "types";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "[method]descriptor.read-via-stream", this::descriptorReadViaStreamImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.write-via-stream", this::descriptorWriteViaStreamImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.append-via-stream", this::descriptorAppendViaStreamImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.advise", this::descriptorAdviseImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.sync-data", this::descriptorSyncDataImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.get-flags", this::descriptorGetFlagsImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.get-type", this::descriptorGetTypeImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.set-size", this::descriptorSetSizeImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.set-times", this::descriptorSetTimesImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.read", this::descriptorReadImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.write", this::descriptorWriteImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.read-directory", this::descriptorReadDirectoryImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.sync", this::descriptorSyncImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.create-directory-at", this::descriptorCreateDirectoryAtImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.stat", this::descriptorStatImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.stat-at", this::descriptorStatAtImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.set-times-at", this::descriptorSetTimesAtImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.link-at", this::descriptorLinkAtImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.open-at", this::descriptorOpenAtImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.readlink-at", this::descriptorReadlinkAtImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.remove-directory-at", this::descriptorRemoveDirectoryAtImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.rename-at", this::descriptorRenameAtImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.symlink-at", this::descriptorSymlinkAtImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.unlink-file-at", this::descriptorUnlinkFileAtImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.is-same-object", this::descriptorIsSameObjectImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.metadata-hash", this::descriptorMetadataHashImpl),
                new ComponentImportFunction(versioned(), "[method]descriptor.metadata-hash-at", this::descriptorMetadataHashAtImpl),
                new ComponentImportFunction(versioned(), "[method]directory-entry-stream.read-directory-entry", this::directoryEntryStreamReadDirectoryEntryImpl),
                new ComponentImportFunction(versioned(), "filesystem-error-code", this::typesFilesystemErrorCodeImpl)
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of(
                new ComponentImportResource(versioned(), "descriptor", this::dropDescriptor),
                new ComponentImportResource(versioned(), "directory-entry-stream", this::dropDirectoryEntryStream),
                new ComponentImportResource(versioned(), "input-stream", this::dropInputStream),
                new ComponentImportResource(versioned(), "output-stream", this::dropOutputStream),
                new ComponentImportResource(versioned(), "error", this::dropError)
            );
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    WitResult descriptorReadViaStream(WasmtimeComponentInstance instance, WitResource self, long offset);

    WitResult descriptorWriteViaStream(WasmtimeComponentInstance instance, WitResource self, long offset);

    WitResult descriptorAppendViaStream(WasmtimeComponentInstance instance, WitResource self);

    WitResult descriptorAdvise(WasmtimeComponentInstance instance, WitResource self, long offset, long length, WitEnum advice);

    WitResult descriptorSyncData(WasmtimeComponentInstance instance, WitResource self);

    WitResult descriptorGetFlags(WasmtimeComponentInstance instance, WitResource self);

    WitResult descriptorGetType(WasmtimeComponentInstance instance, WitResource self);

    WitResult descriptorSetSize(WasmtimeComponentInstance instance, WitResource self, long size);

    WitResult descriptorSetTimes(WasmtimeComponentInstance instance, WitResource self, WitVariant dataAccessTimestamp, WitVariant dataModificationTimestamp);

    WitResult descriptorRead(WasmtimeComponentInstance instance, WitResource self, long length, long offset);

    WitResult descriptorWrite(WasmtimeComponentInstance instance, WitResource self, byte[] buffer, long offset);

    WitResult descriptorReadDirectory(WasmtimeComponentInstance instance, WitResource self);

    WitResult descriptorSync(WasmtimeComponentInstance instance, WitResource self);

    WitResult descriptorCreateDirectoryAt(WasmtimeComponentInstance instance, WitResource self, String path);

    WitResult descriptorStat(WasmtimeComponentInstance instance, WitResource self);

    WitResult descriptorStatAt(WasmtimeComponentInstance instance, WitResource self, Set<String> pathFlags, String path);

    WitResult descriptorSetTimesAt(WasmtimeComponentInstance instance, WitResource self, Set<String> pathFlags, String path, WitVariant dataAccessTimestamp, WitVariant dataModificationTimestamp);

    WitResult descriptorLinkAt(WasmtimeComponentInstance instance, WitResource self, Set<String> oldPathFlags, String oldPath, WitResource newDescriptor, String newPath);

    WitResult descriptorOpenAt(WasmtimeComponentInstance instance, WitResource self, Set<String> pathFlags, String path, Set<String> openFlags, Set<String> flags);

    WitResult descriptorReadlinkAt(WasmtimeComponentInstance instance, WitResource self, String path);

    WitResult descriptorRemoveDirectoryAt(WasmtimeComponentInstance instance, WitResource self, String path);

    WitResult descriptorRenameAt(WasmtimeComponentInstance instance, WitResource self, String oldPath, WitResource newDescriptor, String newPath);

    WitResult descriptorSymlinkAt(WasmtimeComponentInstance instance, WitResource self, String oldPath, String newPath);

    WitResult descriptorUnlinkFileAt(WasmtimeComponentInstance instance, WitResource self, String path);

    boolean descriptorIsSameObject(WasmtimeComponentInstance instance, WitResource self, WitResource other);

    WitResult descriptorMetadataHash(WasmtimeComponentInstance instance, WitResource self);

    WitResult descriptorMetadataHashAt(WasmtimeComponentInstance instance, WitResource self, Set<String> pathFlags, String path);

    WitResult directoryEntryStreamReadDirectoryEntry(WasmtimeComponentInstance instance, WitResource self);

    Optional<Object> typesFilesystemErrorCode(WasmtimeComponentInstance instance, WitResource err);

    void dropDescriptor(int rep);

    void dropDirectoryEntryStream(int rep);

    void dropInputStream(int rep);

    void dropOutputStream(int rep);

    void dropError(int rep);

    private Object[] descriptorReadViaStreamImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long offset = (Long) args[1];
        return new Object[] { descriptorReadViaStream(instance, self, offset) };
    }

    private Object[] descriptorWriteViaStreamImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long offset = (Long) args[1];
        return new Object[] { descriptorWriteViaStream(instance, self, offset) };
    }

    private Object[] descriptorAppendViaStreamImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { descriptorAppendViaStream(instance, self) };
    }

    private Object[] descriptorAdviseImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long offset = (Long) args[1];
        long length = (Long) args[2];
        WitEnum advice = (WitEnum) args[3];
        return new Object[] { descriptorAdvise(instance, self, offset, length, advice) };
    }

    private Object[] descriptorSyncDataImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { descriptorSyncData(instance, self) };
    }

    private Object[] descriptorGetFlagsImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { descriptorGetFlags(instance, self) };
    }

    private Object[] descriptorGetTypeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { descriptorGetType(instance, self) };
    }

    private Object[] descriptorSetSizeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long size = (Long) args[1];
        return new Object[] { descriptorSetSize(instance, self, size) };
    }

    private Object[] descriptorSetTimesImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        WitVariant dataAccessTimestamp = (WitVariant) args[1];
        WitVariant dataModificationTimestamp = (WitVariant) args[2];
        return new Object[] { descriptorSetTimes(instance, self, dataAccessTimestamp, dataModificationTimestamp) };
    }

    private Object[] descriptorReadImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long length = (Long) args[1];
        long offset = (Long) args[2];
        return new Object[] { descriptorRead(instance, self, length, offset) };
    }

    private Object[] descriptorWriteImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        byte[] buffer = (byte[]) args[1];
        long offset = (Long) args[2];
        return new Object[] { descriptorWrite(instance, self, buffer, offset) };
    }

    private Object[] descriptorReadDirectoryImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { descriptorReadDirectory(instance, self) };
    }

    private Object[] descriptorSyncImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { descriptorSync(instance, self) };
    }

    private Object[] descriptorCreateDirectoryAtImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        String path = (String) args[1];
        return new Object[] { descriptorCreateDirectoryAt(instance, self, path) };
    }

    private Object[] descriptorStatImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { descriptorStat(instance, self) };
    }

    private Object[] descriptorStatAtImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        Set<String> pathFlags = (Set<String>) args[1];
        String path = (String) args[2];
        return new Object[] { descriptorStatAt(instance, self, pathFlags, path) };
    }

    private Object[] descriptorSetTimesAtImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        Set<String> pathFlags = (Set<String>) args[1];
        String path = (String) args[2];
        WitVariant dataAccessTimestamp = (WitVariant) args[3];
        WitVariant dataModificationTimestamp = (WitVariant) args[4];
        return new Object[] { descriptorSetTimesAt(instance, self, pathFlags, path, dataAccessTimestamp, dataModificationTimestamp) };
    }

    private Object[] descriptorLinkAtImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        Set<String> oldPathFlags = (Set<String>) args[1];
        String oldPath = (String) args[2];
        WitResource newDescriptor = (WitResource) args[3];
        String newPath = (String) args[4];
        return new Object[] { descriptorLinkAt(instance, self, oldPathFlags, oldPath, newDescriptor, newPath) };
    }

    private Object[] descriptorOpenAtImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        Set<String> pathFlags = (Set<String>) args[1];
        String path = (String) args[2];
        Set<String> openFlags = (Set<String>) args[3];
        Set<String> flags = (Set<String>) args[4];
        return new Object[] { descriptorOpenAt(instance, self, pathFlags, path, openFlags, flags) };
    }

    private Object[] descriptorReadlinkAtImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        String path = (String) args[1];
        return new Object[] { descriptorReadlinkAt(instance, self, path) };
    }

    private Object[] descriptorRemoveDirectoryAtImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        String path = (String) args[1];
        return new Object[] { descriptorRemoveDirectoryAt(instance, self, path) };
    }

    private Object[] descriptorRenameAtImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        String oldPath = (String) args[1];
        WitResource newDescriptor = (WitResource) args[2];
        String newPath = (String) args[3];
        return new Object[] { descriptorRenameAt(instance, self, oldPath, newDescriptor, newPath) };
    }

    private Object[] descriptorSymlinkAtImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        String oldPath = (String) args[1];
        String newPath = (String) args[2];
        return new Object[] { descriptorSymlinkAt(instance, self, oldPath, newPath) };
    }

    private Object[] descriptorUnlinkFileAtImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        String path = (String) args[1];
        return new Object[] { descriptorUnlinkFileAt(instance, self, path) };
    }

    private Object[] descriptorIsSameObjectImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        WitResource other = (WitResource) args[1];
        return new Object[] { descriptorIsSameObject(instance, self, other) };
    }

    private Object[] descriptorMetadataHashImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { descriptorMetadataHash(instance, self) };
    }

    private Object[] descriptorMetadataHashAtImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        Set<String> pathFlags = (Set<String>) args[1];
        String path = (String) args[2];
        return new Object[] { descriptorMetadataHashAt(instance, self, pathFlags, path) };
    }

    private Object[] directoryEntryStreamReadDirectoryEntryImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { directoryEntryStreamReadDirectoryEntry(instance, self) };
    }

    private Object[] typesFilesystemErrorCodeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource err = (WitResource) args[0];
        return new Object[] { typesFilesystemErrorCode(instance, err) };
    }

}
