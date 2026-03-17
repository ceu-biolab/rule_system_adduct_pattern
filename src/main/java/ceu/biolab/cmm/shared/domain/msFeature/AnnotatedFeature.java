package ceu.biolab.cmm.shared.domain.msFeature;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.Data;

@Data
public class AnnotatedFeature {
    @JsonDeserialize(using = IMSFeatureDeserializer.class)
    private IMSFeature feature;
    private List<AnnotationsByAdduct> annotationsByAdducts;

    public AnnotatedFeature() {
        this.annotationsByAdducts = new ArrayList<>();
    }

    public AnnotatedFeature(IMSFeature feature) {
        this.feature = feature;
        this.annotationsByAdducts = new ArrayList<>();
    }

    public void addAnnotationByAdduct(AnnotationsByAdduct annotationsByAdduct) {
        this.annotationsByAdducts.add(annotationsByAdduct);
    }

    static class IMSFeatureDeserializer extends JsonDeserializer<IMSFeature> {
        @Override
        public IMSFeature deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
            JsonNode node = parser.getCodec().readTree(parser);
            if (node == null || node.isNull()) {
                return null;
            }

            double mzValue = node.path("mzValue").asDouble();

            Double intensity = null;
            JsonNode intensityNode = node.get("intensity");
            if (intensityNode != null && intensityNode.isNumber()) {
                intensity = intensityNode.asDouble();
            }

            JsonNode rtNode = node.hasNonNull("rtValue") ? node.get("rtValue") : node.get("retentionTime");
            if (rtNode != null && rtNode.isNumber()) {
                double rtValue = rtNode.asDouble();
                return new LCMSFeature(rtValue, mzValue, intensity);
            }

            return new MSFeature(mzValue, intensity);
        }
    }
}
