package vn.com.fis.consentcore.registry.application.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.com.fis.consentcore.content.api.ContentResource;
import vn.com.fis.consentcore.content.api.ContentRevisionInput;
import vn.com.fis.consentcore.content.api.ContentRevisionResult;
import vn.com.fis.consentcore.enrichment.api.DataPurpose;
import vn.com.fis.consentcore.enrichment.api.ResolvedDataContext;
import vn.com.fis.consentcore.enrichment.api.ResolvedRequirementData;
import vn.com.fis.consentcore.enrichment.api.SelectionCandidate;
import vn.com.fis.consentcore.registry.application.internal.exception.PreparationSelectionException;

@Component
final class ConsentPreparationMaterializer {

    MaterializationResult materialize(
            ContentRevisionResult current,
            ResolvedDataContext resolved,
            Map<String, List<String>> selections) {
        ContentRevisionInput base = toInput(current);
        if (resolved.requirements().isEmpty()) return new MaterializationResult(base, false);

        List<ContentResource> resources = new ArrayList<>(base.resources());
        Map<String,Object> snapshot = new LinkedHashMap<>(base.presentationSnapshot());
        Map<String,Object> enrichmentPresentation = new LinkedHashMap<>();
        Object existingEnrichment = snapshot.get("enrichment");
        if (existingEnrichment instanceof Map<?,?> map) {
            map.forEach((key,value) -> enrichmentPresentation.put(String.valueOf(key), value));
        }

        Set<String> selectionRequirementCodes = new LinkedHashSet<>();
        for (ResolvedRequirementData item : resolved.requirements()) {
            if (item.purpose() == DataPurpose.SELECTION) {
                selectionRequirementCodes.add(item.requirementCode());
                applySelection(resources, item, selections.get(item.requirementCode()));
            } else if (item.purpose() == DataPurpose.CONTEXT && booleanConfig(item, "includeInPresentation", false)) {
                enrichmentPresentation.put(item.requirementCode(), presentationData(item));
            }
        }
        for (String supplied : selections.keySet()) {
            if (!selectionRequirementCodes.contains(supplied)) {
                throw new PreparationSelectionException("Selection was supplied for an unknown/non-selection requirement",
                        Map.of("requirementCode", supplied));
            }
        }
        if (!enrichmentPresentation.isEmpty()) snapshot.put("enrichment", Map.copyOf(enrichmentPresentation));

        ContentRevisionInput prepared = new ContentRevisionInput(base.purposeCode(), base.permissions(), resources,
                base.constraints(), base.obligations(), snapshot);
        return new MaterializationResult(prepared, !prepared.equals(base));
    }

    private void applySelection(
            List<ContentResource> resources,
            ResolvedRequirementData item,
            List<String> selectedIds) {
        if (selectedIds == null) {
            throw new PreparationSelectionException("PSU selection has not been supplied",
                    Map.of("requirementCode", item.requirementCode()));
        }
        int min = intConfig(item, "minSelections", item.required() ? 1 : 0);
        int max = intConfig(item, "maxSelections", Integer.MAX_VALUE);
        if (selectedIds.size() < min || selectedIds.size() > max) {
            throw new PreparationSelectionException("Selection cardinality does not satisfy requirement",
                    Map.of("requirementCode", item.requirementCode(), "minSelections", min,
                            "maxSelections", max == Integer.MAX_VALUE ? "unbounded" : max,
                            "actualSelections", selectedIds.size()));
        }
        Set<String> unique = new LinkedHashSet<>(selectedIds);
        if (unique.size() != selectedIds.size()) {
            throw new PreparationSelectionException("Duplicate candidate IDs are not allowed",
                    Map.of("requirementCode", item.requirementCode()));
        }
        Map<String,SelectionCandidate> candidates = new LinkedHashMap<>();
        for (SelectionCandidate candidate : item.candidates()) candidates.put(candidate.id(), candidate);
        for (String selected : unique) {
            if (!candidates.containsKey(selected)) {
                throw new PreparationSelectionException("Selected candidate is not in the resolved candidate set",
                        Map.of("requirementCode", item.requirementCode(), "candidateId", selected));
            }
        }

        boolean replace = booleanConfig(item, "replaceExistingResourceTypes", true);
        if (replace) {
            Set<String> types = item.candidates().stream().map(SelectionCandidate::resourceType).collect(java.util.stream.Collectors.toSet());
            resources.removeIf(resource -> types.contains(resource.resourceType()));
        }
        for (String selected : unique) {
            SelectionCandidate candidate = candidates.get(selected);
            Map<String,Object> attributes = new LinkedHashMap<>(candidate.attributes());
            attributes.putIfAbsent("displayName", candidate.label());
            attributes.put("sourceDataType", item.dataType());
            attributes.put("sourceRequirement", item.requirementCode());
            resources.add(new ContentResource(candidate.resourceType(), candidate.id(), attributes));
        }
    }

    private static Map<String,Object> presentationData(ResolvedRequirementData item) {
        List<String> fields = stringList(item.requirementConfiguration().get("presentationFields"));
        if (fields.isEmpty()) return item.data();
        Map<String,Object> filtered = new LinkedHashMap<>();
        for (String field : fields) if (item.data().containsKey(field)) filtered.put(field, item.data().get(field));
        return Map.copyOf(filtered);
    }

    private static ContentRevisionInput toInput(ContentRevisionResult current) {
        return new ContentRevisionInput(current.purposeCode(), current.permissions(), current.resources(),
                current.constraints(), current.obligations(), current.presentationSnapshot());
    }

    private static boolean booleanConfig(ResolvedRequirementData item, String key, boolean fallback) {
        Object value = item.requirementConfiguration().get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static int intConfig(ResolvedRequirementData item, String key, int fallback) {
        Object value = item.requirementConfiguration().get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream().filter(java.util.Objects::nonNull).map(Object::toString)
                .filter(item -> !item.isBlank()).toList();
    }

    record MaterializationResult(ContentRevisionInput content, boolean changed) { }
}
