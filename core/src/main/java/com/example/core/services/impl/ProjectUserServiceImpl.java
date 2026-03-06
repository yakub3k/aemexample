package com.example.core.services.impl;

import com.example.core.services.ProjectUserService;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of ProjektUserService using projekt-user-system service user.
 */
@Component(service = ProjectUserService.class, immediate = true)
public class ProjectUserServiceImpl implements ProjectUserService {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectUserServiceImpl.class);
    private static final String SERVICE_USER_SUBSERVICE = "projekt-user-system";

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Activate
    protected void activate() {
        LOG.info("ProjektUserService activated");
    }

    @Override
    public ResourceResolver getServiceResourceResolver() {
        Map<String, Object> param = new HashMap<>();
        param.put(ResourceResolverFactory.SUBSERVICE, SERVICE_USER_SUBSERVICE);

        try {
            return resourceResolverFactory.getServiceResourceResolver(param);
        } catch (LoginException e) {
            LOG.error("Failed to get service resource resolver for subservice: {}", SERVICE_USER_SUBSERVICE, e);
            return null;
        }
    }

    @Override
    public void executeWithServiceUser(ResourceResolverOperation operation) {
        ResourceResolver resolver = null;
        try {
            resolver = getServiceResourceResolver();
            if (resolver != null) {
                operation.execute(resolver);
            } else {
                LOG.error("Failed to obtain service resource resolver");
            }
        } catch (Exception e) {
            LOG.error("Error executing operation with service user", e);
        } finally {
            if (resolver != null && resolver.isLive()) {
                resolver.close();
            }
        }
    }
}
