package com.oilpricedbmanager.external.opinet;

import com.oilpricedbmanager.domain.OpinetStationPrice;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

@Component
public class OpinetXmlParser {
    public List<OpinetStationPrice> parseAroundAll(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList oilNodes = document.getElementsByTagName("OIL");
            List<OpinetStationPrice> results = new ArrayList<>();
            for (int i = 0; i < oilNodes.getLength(); i++) {
                try {
                    Element oil = (Element) oilNodes.item(i);
                    String uniId = text(oil, "UNI_ID");
                    String stationName = text(oil, "OS_NM");
                    String priceText = text(oil, "PRICE");
                    String xText = text(oil, "GIS_X_COOR");
                    String yText = text(oil, "GIS_Y_COOR");
                    if (uniId == null || stationName == null || priceText == null || xText == null || yText == null) {
                        continue;
                    }
                    String pollDivCd = text(oil, "POLL_DIV_CD");
                    if (pollDivCd == null) {
                        pollDivCd = text(oil, "POLL_DIV_CO");
                    }
                    results.add(new OpinetStationPrice(
                            uniId,
                            pollDivCd,
                            stationName,
                            parseInt(priceText),
                            parseNullableInt(text(oil, "DISTANCE")),
                            parseDouble(xText),
                            parseDouble(yText)
                    ));
                } catch (RuntimeException ignored) {
                    // Opinet sometimes includes malformed rows; skip only the bad row.
                }
            }
            return results;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse Opinet aroundAll XML", exception);
        }
    }

    private static String text(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return null;
        }
        String value = nodes.item(0).getTextContent().trim();
        return value.isEmpty() ? null : value;
    }

    private static int parseInt(String value) {
        return Integer.parseInt(value.replace(",", ""));
    }

    private static Integer parseNullableInt(String value) {
        return value == null ? null : (int) Math.round(Double.parseDouble(value.replace(",", "")));
    }

    private static double parseDouble(String value) {
        return Double.parseDouble(value.replace(",", ""));
    }
}
