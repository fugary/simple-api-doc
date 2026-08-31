package com.fugary.simple.api.web.vo.imports;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportStatisticsVoTest {

    @Test
    void testStatisticsAccumulationAndSummary() {
        ImportStatisticsVo vo = new ImportStatisticsVo();
        vo.addDocAdded();
        vo.addDocAdded();
        vo.addDocUpdated();
        vo.addDocUnchanged();
        vo.addDocUnchanged();
        vo.addDocUnchanged();
        vo.addDocLocked();
        vo.addFolderAdded();
        vo.setCostTime(123);

        assertThat(vo.getDocAdded()).isEqualTo(2);
        assertThat(vo.getDocUpdated()).isEqualTo(1);
        assertThat(vo.getDocUnchanged()).isEqualTo(3);
        assertThat(vo.getDocLocked()).isEqualTo(1);
        assertThat(vo.getFolderAdded()).isEqualTo(1);
        assertThat(vo.getCostTime()).isEqualTo(123);
        assertThat(vo.getTotalDocs()).isEqualTo(7);
        assertThat(vo.toCompactSummary()).isEqualTo("+2 ~1 =3");
    }
}
