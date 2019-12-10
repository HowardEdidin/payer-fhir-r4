package org.opencds.cqf.r4.providers;

/*-
 * #%L
 * HAPI FHIR JPA Server
 * %%
 * Copyright (C) 2014 - 2019 University Health Network
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.dao.DaoMethodOutcome;
import ca.uhn.fhir.jpa.dao.IFhirSystemDao;
import ca.uhn.fhir.jpa.rp.r4.ClaimResourceProvider;
import ca.uhn.fhir.jpa.rp.r4.MeasureResourceProvider;
import ca.uhn.fhir.jpa.subscription.ISubscriptionTriggeringSvc;
//import ca.uhn.fhir.jpa.model.util.JpaConstants;
import ca.uhn.fhir.model.dstu2.valueset.ResourceTypeEnum;
import ca.uhn.fhir.model.valueset.BundleEntrySearchModeEnum;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.UriParam;
import ca.uhn.fhir.rest.server.IResourceProvider;

import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;

import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.gson.JsonObject;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.List;
import java.util.Random;
import java.util.ArrayList;
import java.util.Date;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.io.FileWriter;  
import java.io.FileReader;  
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
//import ca.uhn.fhir.jpa.rp.r4.ClaimResponseResourceProvider;



public class CustomClaimTrigger extends ClaimResourceProvider{
//	public static final String RESOURCE_ID = "resourceId";
//	public static final String SEARCH_URL = "searchUrl";
	@Autowired
	private FhirContext myFhirContext;
//	@Autowired
//	private ISubscriptionTriggeringSvc mySubscriptionTriggeringSvc;
	private JpaDataProvider provider;
	private IFhirSystemDao systemDao;
	FHIRBundleResourceProvider bundleProvider;
	FHIRClaimResponseProvider claimResponseProvider;
//	@Operation(name = JpaConstants.OPERATION_TRIGGER_SUBSCRIPTION)
//	public IBaseParameters triggerSubscription(
//		@OperationParam(name = RESOURCE_ID, min = 0, max = OperationParam.MAX_UNLIMITED) List<UriParam> theResourceIds,
//		@OperationParam(name = SEARCH_URL, min = 0, max = OperationParam.MAX_UNLIMITED) List<StringParam> theSearchUrls
//	) {
//		return mySubscriptionTriggeringSvc.triggerSubscription(theResourceIds, theSearchUrls, null);
//	}
//
//	@Operation(name = JpaConstants.OPERATION_TRIGGER_SUBSCRIPTION)
//	public IBaseParameters triggerSubscription(
//		@IdParam IIdType theSubscriptionId,
//		@OperationParam(name = RESOURCE_ID, min = 0, max = OperationParam.MAX_UNLIMITED) List<UriParam> theResourceIds,
//		@OperationParam(name = SEARCH_URL, min = 0, max = OperationParam.MAX_UNLIMITED) List<StringParam> theSearchUrls
//	) {
//		return mySubscriptionTriggeringSvc.triggerSubscription(theResourceIds, theSearchUrls, theSubscriptionId);
//	}
	
	public CustomClaimTrigger(JpaDataProvider dataProvider, IFhirSystemDao systemDao, FHIRClaimResponseProvider claimResponseProvider) {
		this.provider = dataProvider;
		this.systemDao = systemDao;
		this.bundleProvider = (FHIRBundleResourceProvider) dataProvider.resolveResourceProvider("Bundle");
		this.claimResponseProvider = claimResponseProvider ;
		
	}
	
	public String getSaltString() {
	      String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
	      StringBuilder salt = new StringBuilder();
	      Random rnd = new Random();
	      while (salt.length() < 16) { // length of the random string.
	          int index = (int) (rnd.nextFloat() * SALTCHARS.length());
	          salt.append(SALTCHARS.charAt(index));
	      }
	      String saltStr = salt.toString();
	      return saltStr;

	 }

//	@Create
	@Operation(name="$submit", idempotent=true)
	public Bundle claimSubmit(RequestDetails details,
			@OperationParam(name = "claim", min = 1, max = 1, type = Bundle.class) Bundle bundle
		) throws RuntimeException{
		
		ClaimResponse retVal = new ClaimResponse();
		Bundle collectionBundle = new Bundle().setType(Bundle.BundleType.COLLECTION);
//		retVal.setId(new IdType("ClaimResponse", "31e6e675-3ecd-4360-9e40-ec7d145fa96d", "1"));
//		ClaimResponse claimRes = new ClaimResponse();
		Bundle responseBundle = new Bundle();
		Bundle createdBundle = new Bundle();
		String claimURL = "";
		String patientId = "";
		String patientIdentifier = "";
		String claimIdentifier = "";
		
		for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			collectionBundle.addEntry(entry);
			System.out.println("ResType : "+entry.getResource().getResourceType());
			if(entry.getResource().getResourceType().toString().equals("Claim")){
				try {
					Claim claim = (Claim) entry.getResource();
					System.out.println("Identifier"+claim.getIdentifier());
					claimIdentifier = ((Claim) entry.getResource()).getIdentifier().get(0).getValue();
				}
				catch(Exception e){
					e.printStackTrace();
				}
			}
			else if(entry.getResource().getResourceType().toString().equals("Patient")) {
				try {
					System.out.println("000ResType : "+entry.getResource().getResourceType());
					Patient patient = (Patient) entry.getResource();
					System.out.println("Identifier"+patient.getIdentifier());
					patientIdentifier = ((Patient) entry.getResource()).getIdentifier().get(0).getValue();
				}
				catch(Exception e){
					e.printStackTrace();
				}
			}
		}
		
		try {
			DaoMethodOutcome bundleOutcome= this.bundleProvider.getDao().create(collectionBundle);
			createdBundle  = (Bundle) bundleOutcome.getResource();
			
			int i = 0;

		}
		catch(Exception e){
			e.printStackTrace();
			throw new RuntimeException(e.getLocalizedMessage());
		}
			
		try {
				
//			String basePathOfClass = getClass().getProtectionDomain().getCodeSource().getLocation().getFile();
//				System.out.println(basePathOfClass);
//			String[] splitPath = basePathOfClass.split("/target/classes");
			
//			System.out.println(splitPath);
//			if(splitPath.length > 1) {

	    	  IParser jsonParser = details.getFhirContext().newJsonParser();
	    	  String jsonStr =jsonParser.encodeResourceToString(bundle);
	    	  System.out.println("JSON:\n"+jsonStr);
	    	  StringBuilder sb = new StringBuilder();
	    	  URL url = new URL("http://cdex.mettles.com:5000/xmlx12");
	    	  byte[] postDataBytes = jsonStr.getBytes("UTF-8");
	    	  HttpURLConnection conn = (HttpURLConnection)url.openConnection();
		      conn.setRequestMethod("POST");
	          conn.setRequestProperty("Content-Type", "application/json");
	          conn.setRequestProperty("Accept","application/json");
	          conn.setDoOutput(true);
	          conn.getOutputStream().write(postDataBytes);
	          BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
	          String line =null;
	          while((line=in.readLine())!= null){
	            sb.append(line);
	          }
	          String result = sb.toString();
	          JSONObject response = new JSONObject(result);
	          System.out.print("JSON:"+response.toString());
	          if(response.has("x12_response")) {
	        	  String x12_generated = response.getString("x12_response");
	        	  System.out.println("----------X12 Generated---------");
	        	  System.out.println(x12_generated);
	        	  
//		        	  ClaimResponse.NoteComponent note = new ClaimResponse.NoteComponent();
//		        	  note.setText(x12_generated.replace("\n", "").replace("\r", ""));
//		        	  List<ClaimResponse.NoteComponent> theProcessNote = new ArrayList<ClaimResponse.NoteComponent>();
//		        	  theProcessNote.add(note);
	        	  //retVal.setProcessNote(theProcessNote);
	        	  
	          }
			}
		catch(Exception e){
	  			e.printStackTrace();
	  			throw new RuntimeException(e.getLocalizedMessage());
		}
		try {
		           
	    	  System.out.println("----------X12 Generated--------- \n");
//	        	  System.out.println(x12_generated);
        	  System.out.println("\n------------------- \n");
	          CodeableConcept typeCodeableConcept = new CodeableConcept();
	          Coding typeCoding = new Coding();
	          typeCoding.setCode("professional");
	          typeCoding.setSystem("http://terminology.hl7.org/CodeSystem/claim-type");
	          typeCoding.setDisplay("Professional");
	          typeCodeableConcept.addCoding(typeCoding);
	          Reference patientRef = new Reference();
	         
	          if(!patientIdentifier.isEmpty()) {
	        	  Identifier patientIdentifierObj = new Identifier();
	        	  patientIdentifierObj.setValue(patientIdentifier);
		          patientRef.setIdentifier(patientIdentifierObj);
		          retVal.setPatient(patientRef);
	          }
	          
//		          
	          retVal.setCreated(new Date());
	          retVal.setType(typeCodeableConcept);
	          retVal.setUse(ClaimResponse.Use.PREAUTHORIZATION);
	          retVal.setStatus(ClaimResponse.ClaimResponseStatus.ACTIVE);
	          retVal.setOutcome(ClaimResponse.RemittanceOutcome.QUEUED);
//		          DaoMethodOutcome claimOutcome = this.getDao().create((Claim) createdBundle.getEntryFirstRep().getResource());
//		          Claim claim = (Claim)claimOutcome.getResource();
	          Reference reqRef = new Reference();
	          if(!claimIdentifier.isEmpty()) {
	        	  Identifier claimIdentifierObj = new Identifier();
	        	  claimIdentifierObj.setValue(claimIdentifier);
	        	  reqRef.setIdentifier(claimIdentifierObj);
	          }
	          
	          retVal.setRequest(reqRef);
	          retVal.setPreAuthRef(getSaltString());
	          
	          System.out.println("\n------------------- \n"+claimResponseProvider.getDao());
	          DaoMethodOutcome claimResponseOutcome= claimResponseProvider.getDao().create(retVal);
	          ClaimResponse claimResponse = (ClaimResponse) claimResponseOutcome.getResource();
	          System.out.println("\n-----ClaimResss-------------- \n"+claimResponse.getId());

	          Bundle.BundleEntryComponent transactionEntry = new Bundle.BundleEntryComponent().setResource(claimResponse);
//		          collectionBundle.addEntry(transactionEntry);
			  responseBundle.addEntry(transactionEntry);
			  for (Bundle.BundleEntryComponent entry :  createdBundle.getEntry()) {
				  responseBundle.addEntry(entry);
			   }
			  responseBundle.setId(createdBundle.getId());
			  responseBundle.setType(Bundle.BundleType.COLLECTION);
			  return responseBundle;
	          //		          System.out.println("Output");
//		          System.out.println(result);
//		          
	        
	    	  
//			}
//		   retVal.addName().addGiven(reqJson.get("name").toString());
		   // Populate bundle with matching resources
		   
		}
		catch(Exception ex) {
			ex.printStackTrace();
		}
		return collectionBundle;
	}
	
	

//	@Override
//	public Class<? extends IBaseResource> getResourceType() {
//		System.out.println( "\n /n solo level \n /n"); 
//		return myFhirContext.getResourceDefinition(ResourceTypeEnum.SUBSCRIPTION.getCode()).getImplementingClass();
//	}

	@Override
	public Class<Claim> getResourceType() {
		
		System.out.println("\n PAtient GET \n");
		
		return Claim.class;
	}
}