package com.oilpricedbmanager.external.opinet;

import com.oilpricedbmanager.domain.OpinetStationPrice;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpinetXmlParserTest {
    private final OpinetXmlParser parser = new OpinetXmlParser();

    @Test
    void parsesAroundAllOilNodes() {
        String xml = """
                <RESULT>
                  <OIL>
                    <UNI_ID>A000001</UNI_ID>
                    <POLL_DIV_CD>SKE</POLL_DIV_CD>
                    <OS_NM>Sample Station</OS_NM>
                    <PRICE>1,650</PRICE>
                    <DISTANCE>120</DISTANCE>
                    <GIS_X_COOR>314681.8</GIS_X_COOR>
                    <GIS_Y_COOR>544837</GIS_Y_COOR>
                  </OIL>
                </RESULT>
                """;

        List<OpinetStationPrice> prices = parser.parseAroundAll(xml);

        assertThat(prices).hasSize(1);
        assertThat(prices.getFirst().uniId()).isEqualTo("A000001");
        assertThat(prices.getFirst().price()).isEqualTo(1650);
        assertThat(prices.getFirst().katecX()).isEqualTo(314681.8);
    }
}