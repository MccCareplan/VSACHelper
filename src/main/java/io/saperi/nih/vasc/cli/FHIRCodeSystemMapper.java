package io.saperi.nih.vasc.cli;

import java.util.HashMap;

public class FHIRCodeSystemMapper {

    private static HashMap<String, String> codeSystemMap = new HashMap<>();
    static {
        //SNOMED Alias
        codeSystemMap.put("http://snomed.info/sct", "http://snomed.info/sct");
        codeSystemMap.put("2.16.840.1.113883.6.96", "http://snomed.info/sct");
        codeSystemMap.put("SNOMEDCT", "http://snomed.info/sct");

        //LOINC
        codeSystemMap.put("http://loinc.org", "http://loinc.org");
        codeSystemMap.put("2.16.840.1.113883.6.1", "http://loinc.org");
        codeSystemMap.put("LOINC", "http://loinc.org");

        //RxNorm
        codeSystemMap.put("http://www.nlm.nih.gov/research/umls/rxnorm","http://www.nlm.nih.gov/research/umls/rxnorm");
        codeSystemMap.put("2.16.840.1.113883.6.88", "http://www.nlm.nih.gov/research/umls/rxnorm");
        codeSystemMap.put("RxNorm", "http://www.nlm.nih.gov/research/umls/rxnorm");

        //ICD10
        codeSystemMap.put("http://hl7.org/fhir/sid/icd-10","http://hl7.org/fhir/sid/icd-10");
        codeSystemMap.put("2.16.840.1.113883.6.3","http://hl7.org/fhir/sid/icd-10");
        codeSystemMap.put("ICD10","http://hl7.org/fhir/sid/icd-10");
        codeSystemMap.put("ICD-10","http://hl7.org/fhir/sid/icd-10");

        //ICD10-CM
        codeSystemMap.put("http://hl7.org/fhir/sid/icd-10-cm","http://hl7.org/fhir/sid/icd-10-cm");
        codeSystemMap.put("2.16.840.1.113883.6.90","http://hl7.org/fhir/sid/icd-10-cm");
        codeSystemMap.put("ICD10CM","http://hl7.org/fhir/sid/icd-10-cm");
        codeSystemMap.put("ICD-10-CM","http://hl7.org/fhir/sid/icd-10-cm");

        //CPT
        codeSystemMap.put("http://www.ama-assn.org/go/cpt","http://www.ama-assn.org/go/cpt");
        codeSystemMap.put("2.16.840.1.113883.6.12","http://www.ama-assn.org/go/cpt");
        codeSystemMap.put("CPT","http://www.ama-assn.org/go/cpt");
      }

    public static String getFHIRCodeSystem(String codeSystem)
    {
        if (codeSystemMap.containsKey(codeSystem))
        {
            return codeSystemMap.get(codeSystem);
        }
        return codeSystem;
    }
}
