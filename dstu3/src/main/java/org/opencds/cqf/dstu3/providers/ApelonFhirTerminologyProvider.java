package org.opencds.cqf.dstu3.providers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.Parameters;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.opencds.cqf.cql.runtime.Code;
import org.opencds.cqf.cql.terminology.ValueSetInfo;
import org.opencds.cqf.cql.terminology.fhir.FhirTerminologyProvider;

import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;

public class ApelonFhirTerminologyProvider extends FhirTerminologyProvider
    {
        private Map<String, List<Code>> cache = new HashMap<>();

        @Override
        public Iterable<Code> expand(ValueSetInfo valueSet) throws ResourceNotFoundException {
            String id = valueSet.getId();
            if (this.cache.containsKey(id)) {
                return this.cache.get(id);
            }

            String url = this.resolveByIdentifier(valueSet);

            Parameters respParam = this.getFhirClient()
                    .operation()
                    .onType(ValueSet.class)
                    .named("expand")
                    .withSearchParameter(Parameters.class, "url", new StringParam(url))
                    .andSearchParameter("includeDefinition", new StringParam("true"))
                    .useHttpGet()
                    .execute();
    
            ValueSet expanded = (ValueSet) respParam.getParameter().get(0).getResource();
            List<Code> codes = new ArrayList<>();
            for (ValueSet.ValueSetExpansionContainsComponent codeInfo : expanded.getExpansion().getContains()) {
                Code nextCode = new Code()
                        .withCode(codeInfo.getCode())
                        .withSystem(codeInfo.getSystem())
                        .withVersion(codeInfo.getVersion())
                        .withDisplay(codeInfo.getDisplay());
                codes.add(nextCode);
            }

            this.cache.put(id, codes);
            return codes;
        }

        public String resolveByIdentifier(ValueSetInfo valueSet) {
            String valueSetId = valueSet.getId();
            
            valueSetId = valueSetId.replace("urn:oid:", "");

            IQuery<Bundle> bundleQuery = this.getFhirClient()
                .search()
                .byUrl("ValueSet?identifier=" + valueSetId)
                .returnBundle(Bundle.class)
                .accept("application/fhir+xml");
                
            Bundle searchResults = bundleQuery.execute();

            if (searchResults.hasEntry()) {
                for (BundleEntryComponent bec : searchResults.getEntry()) {
                    if (bec.hasResource()) {
                        String id = bec.getResource().getIdElement().getIdPart();
                        if (id.equals(valueSetId)) {
                            return ((ValueSet)bec.getResource()).getUrl();
                        }
                    }

                }
            }

            return null;
        }
    }