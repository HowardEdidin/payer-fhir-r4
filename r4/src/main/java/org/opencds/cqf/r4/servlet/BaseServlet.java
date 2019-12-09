package org.opencds.cqf.r4.servlet;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.dao.DaoConfig;
import ca.uhn.fhir.jpa.dao.IFhirSystemDao;
import ca.uhn.fhir.jpa.provider.r4.JpaConformanceProviderR4;
import ca.uhn.fhir.jpa.provider.r4.JpaSystemProviderR4;
import ca.uhn.fhir.jpa.provider.r4.TerminologyUploaderProviderR4;
import ca.uhn.fhir.jpa.rp.r4.*;
import ca.uhn.fhir.jpa.rp.r4.LibraryResourceProvider;
import ca.uhn.fhir.jpa.search.DatabaseBackedPagingProvider;
import ca.uhn.fhir.jpa.term.IHapiTerminologySvcR4;
import ca.uhn.fhir.narrative.DefaultThymeleafNarrativeGenerator;
import ca.uhn.fhir.rest.server.HardcodedServerAddressStrategy;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.CorsInterceptor;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Meta;
import org.opencds.cqf.config.HapiProperties;
import org.opencds.cqf.cql.terminology.TerminologyProvider;
import org.opencds.cqf.r4.providers.*;
import org.springframework.context.ApplicationContext;
import org.springframework.web.cors.CorsConfiguration;

import javax.servlet.ServletException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.opencds.cqf.config.ClientAuthorizationInterceptor;
//import ca.uhn.fhir.jpa.starter.CustomClaimTrigger;


public class BaseServlet extends RestfulServer
{
    private JpaDataProvider provider;
    public JpaDataProvider getProvider() {
        return provider;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void initialize() throws ServletException
    {
        super.initialize();

        ApplicationContext appCtx = (ApplicationContext) getServletContext().getAttribute("org.springframework.web.context.WebApplicationContext.ROOT");

        List<IResourceProvider> resourceProviders = appCtx.getBean("myResourceProvidersR4", List.class);
        Object systemProvider = appCtx.getBean("mySystemProviderR4", JpaSystemProviderR4.class);
        List<Object> plainProviders = new ArrayList<>();

        setFhirContext(appCtx.getBean(FhirContext.class));

        registerProvider(systemProvider);

        IFhirSystemDao<Bundle, Meta> systemDao = appCtx.getBean("mySystemDaoR4", IFhirSystemDao.class);
        JpaConformanceProviderR4 confProvider = new JpaConformanceProviderR4(this, systemDao, appCtx.getBean(DaoConfig.class));
        confProvider.setImplementationDescription("CQF Ruler FHIR R4 Server");
        setServerConformanceProvider(confProvider);

        plainProviders.add(appCtx.getBean(TerminologyUploaderProviderR4.class));
        provider = new JpaDataProvider(resourceProviders);
        TerminologyProvider terminologyProvider = new JpaTerminologyProvider(appCtx.getBean("terminologyService", IHapiTerminologySvcR4.class), getFhirContext(), (ValueSetResourceProvider) provider.resolveResourceProvider("ValueSet"));
        provider.setTerminologyProvider(terminologyProvider);
        resolveResourceProviders(provider, systemDao);

        CqlExecutionProvider cql = new CqlExecutionProvider(provider);
        plainProviders.add(cql);

        registerProviders(resourceProviders);

        CodeSystemUpdateProvider csUpdate = new CodeSystemUpdateProvider(provider);
        plainProviders.add(csUpdate);

        registerProviders(plainProviders);
        setResourceProviders(resourceProviders);

        CdsHooksServlet.provider = provider;

        /*
         * ETag Support
         */
        setETagSupport(HapiProperties.getEtagSupport());

        /*
         * This server tries to dynamically generate narratives
         */
        FhirContext ctx = getFhirContext();
        ctx.setNarrativeGenerator(new DefaultThymeleafNarrativeGenerator());

        /*
         * Default to JSON and pretty printing
         */
        setDefaultPrettyPrint(HapiProperties.getDefaultPrettyPrint());

        /*
         * Default encoding
         */
        setDefaultResponseEncoding(HapiProperties.getDefaultEncoding());

        /*
         * This configures the server to page search results to and from
         * the database, instead of only paging them to memory. This may mean
         * a performance hit when performing searches that return lots of results,
         * but makes the server much more scalable.
         */
        setPagingProvider(appCtx.getBean(DatabaseBackedPagingProvider.class));

        /*
         * This interceptor formats the output using nice colourful
         * HTML output when the request is detected to come from a
         * browser.
         */
        ResponseHighlighterInterceptor responseHighlighterInterceptor = appCtx.getBean(ResponseHighlighterInterceptor.class);
        this.registerInterceptor(responseHighlighterInterceptor);
        
        FHIRClaimResponseProvider claimResProvider = new FHIRClaimResponseProvider(provider);
        ClaimResponseResourceProvider jpaClaimResponseProvider = (ClaimResponseResourceProvider) provider.resolveResourceProvider("ClaimResponse");
        claimResProvider.setDao(jpaClaimResponseProvider.getDao());
        claimResProvider.setContext(jpaClaimResponseProvider.getContext());

        

//        register(bundleProvider, provider.getCollectionProviders());
//        FHIRClaimResponseProvider claimResProvider = new FHIRClaimResponseProvider(provider, systemDao);
        registerProvider(claimResProvider);
        
        CustomClaimTrigger customClaimProvider = new CustomClaimTrigger(provider, systemDao,claimResProvider);
        ClaimResourceProvider jpaClaimProvider = (ClaimResourceProvider) provider.resolveResourceProvider("Claim");
        customClaimProvider.setDao(jpaClaimProvider.getDao());
        customClaimProvider.setContext(jpaClaimProvider.getContext());
        
        registerProvider(customClaimProvider);
        
        ClientAuthorizationInterceptor authInterceptor =  appCtx.getBean(ClientAuthorizationInterceptor.class);
        this.registerInterceptor(authInterceptor);
        /*
         * If you are hosting this server at a specific DNS name, the server will try to
         * figure out the FHIR base URL based on what the web container tells it, but
         * this doesn't always work. If you are setting links in your search bundles that
         * just refer to "localhost", you might want to use a server address strategy:
         */
        String serverAddress = HapiProperties.getServerAddress();
        if (serverAddress != null && serverAddress.length() > 0)
        {
            setServerAddressStrategy(new HardcodedServerAddressStrategy(serverAddress));
        }

        registerProvider(appCtx.getBean(TerminologyUploaderProviderR4.class));

        if (HapiProperties.getCorsEnabled())
        {
            CorsConfiguration config = new CorsConfiguration();
            config.addAllowedHeader("x-fhir-starter");
            config.addAllowedHeader("Origin");
            config.addAllowedHeader("Accept");
            config.addAllowedHeader("X-Requested-With");
            config.addAllowedHeader("Content-Type");
            config.addAllowedHeader("Authorization");
            config.addAllowedHeader("Cache-Control");
            config.addAllowedHeader("Prefer");
            config.addAllowedOrigin(HapiProperties.getCorsAllowedOrigin());

            config.addExposedHeader("Location");
            config.addExposedHeader("Content-Location");
            config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

            // Create the interceptor and register it
            CorsInterceptor interceptor = new CorsInterceptor(config);
            registerInterceptor(interceptor);
        }
    }

    private void resolveResourceProviders(JpaDataProvider provider, IFhirSystemDao systemDao)
            throws ServletException
    {
        NarrativeProvider narrativeProvider = new NarrativeProvider();
        HQMFProvider hqmfProvider = new HQMFProvider();

        // ValueSet processing
        FHIRValueSetResourceProvider valueSetProvider =
                new FHIRValueSetResourceProvider(
                        (CodeSystemResourceProvider) provider.resolveResourceProvider("CodeSystem")
                );
        ValueSetResourceProvider jpaValueSetProvider = (ValueSetResourceProvider) provider.resolveResourceProvider("ValueSet");
        valueSetProvider.setDao(jpaValueSetProvider.getDao());
        valueSetProvider.setContext(jpaValueSetProvider.getContext());

        try {
            unregister(jpaValueSetProvider, provider.getCollectionProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(valueSetProvider, provider.getCollectionProviders());

        // Bundle processing
        FHIRBundleResourceProvider bundleProvider = new FHIRBundleResourceProvider(provider);
        BundleResourceProvider jpaBundleProvider = (BundleResourceProvider) provider.resolveResourceProvider("Bundle");
        bundleProvider.setDao(jpaBundleProvider.getDao());
        bundleProvider.setContext(jpaBundleProvider.getContext());

        try {
            unregister(jpaBundleProvider, provider.getCollectionProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(bundleProvider, provider.getCollectionProviders());

        //Library processing
        NarrativeLibraryResourceProvider libraryProvider = new NarrativeLibraryResourceProvider(narrativeProvider);
        LibraryResourceProvider jpaLibraryProvider =
            (LibraryResourceProvider) provider.resolveResourceProvider("Library");
        libraryProvider.setDao(jpaLibraryProvider.getDao());
        libraryProvider.setContext(jpaLibraryProvider.getContext());

        try {
            unregister(jpaLibraryProvider, provider.getCollectionProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(libraryProvider, provider.getCollectionProviders());

        // Measure processing
        FHIRMeasureResourceProvider measureProvider = new FHIRMeasureResourceProvider(provider, systemDao, narrativeProvider, hqmfProvider);
        MeasureResourceProvider jpaMeasureProvider = (MeasureResourceProvider) provider.resolveResourceProvider("Measure");
        measureProvider.setDao(jpaMeasureProvider.getDao());
        measureProvider.setContext(jpaMeasureProvider.getContext());

        try {
            unregister(jpaMeasureProvider, provider.getCollectionProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(measureProvider, provider.getCollectionProviders());

        
        
        // ActivityDefinition processing
        FHIRActivityDefinitionResourceProvider actDefProvider = new FHIRActivityDefinitionResourceProvider(provider);
        ActivityDefinitionResourceProvider jpaActDefProvider = (ActivityDefinitionResourceProvider) provider.resolveResourceProvider("ActivityDefinition");
        actDefProvider.setDao(jpaActDefProvider.getDao());
        actDefProvider.setContext(jpaActDefProvider.getContext());

        try {
            unregister(jpaActDefProvider, provider.getCollectionProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(actDefProvider, provider.getCollectionProviders());

        // PlanDefinition processing
        FHIRPlanDefinitionResourceProvider planDefProvider = new FHIRPlanDefinitionResourceProvider(provider);
        PlanDefinitionResourceProvider jpaPlanDefProvider = (PlanDefinitionResourceProvider) provider.resolveResourceProvider("PlanDefinition");
        planDefProvider.setDao(jpaPlanDefProvider.getDao());
        planDefProvider.setContext(jpaPlanDefProvider.getContext());

        try {
            unregister(jpaPlanDefProvider, provider.getCollectionProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(planDefProvider, provider.getCollectionProviders());

        // Endpoint processing
        FHIREndpointProvider endpointProvider = new FHIREndpointProvider(provider, systemDao);
        EndpointResourceProvider jpaEndpointProvider = (EndpointResourceProvider) provider.resolveResourceProvider("Endpoint");
        endpointProvider.setDao(jpaEndpointProvider.getDao());
        endpointProvider.setContext(jpaEndpointProvider.getContext());

        try {
            unregister(jpaEndpointProvider, provider.getCollectionProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(endpointProvider, provider.getCollectionProviders());
    }

    private void register(IResourceProvider provider, Collection<IResourceProvider> providers) {
        providers.add(provider);
    }

    private void unregister(IResourceProvider provider, Collection<IResourceProvider> providers) {
        providers.remove(provider);
    }
}
