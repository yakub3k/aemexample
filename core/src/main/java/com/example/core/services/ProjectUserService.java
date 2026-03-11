package com.example.core.services;

import org.apache.sling.api.resource.ResourceResolver;

import java.io.IOException;

/**
 * Service interface for operations using projekt-user-system service user.
 */
public interface ProjectUserService {

    /**
     * Get a resource resolver with projekt-user-system service user credentials.
     *
     * @return ResourceResolver with service user permissions
     */
    ResourceResolver getServiceResourceResolver();

    /**
     * Execute operation with service user context.
     *
     * @param operation The operation to execute
     */
    void executeWithServiceUser(ResourceResolverOperation operation);

    /**
     * Functional interface for operations that need service user context.
     */
    @FunctionalInterface
    interface ResourceResolverOperation {
        void execute(ResourceResolver resolver) throws IOException;
    }
}
