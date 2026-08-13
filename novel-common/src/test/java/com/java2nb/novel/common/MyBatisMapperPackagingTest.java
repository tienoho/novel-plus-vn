package com.java2nb.novel.common;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisMapperPackagingTest {

    @Test
    void everyXmlMapperStaysInsideConfiguredMappingDirectory() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path mybatis = module.resolve("src/main/resources/mybatis");
        Path mapping = mybatis.resolve("mapping");

        try (var files = Files.list(mybatis)) {
            List<Path> misplacedMappers = files
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith("Mapper.xml"))
                .toList();
            assertThat(misplacedMappers).isEmpty();
        }

        assertThat(mapping.resolve("FinancialVoucherMapper.xml")).exists();
        assertThat(mapping.resolve("SysAuditLogMapper.xml")).exists();
        assertThat(mapping.resolve("User2faMapper.xml")).exists();
        assertThat(mapping.resolve("MonthlyTicketMapper.xml")).exists();
        assertThat(mapping.resolve("MonthlyRankingMapper.xml")).exists();
        assertThat(mapping.resolve("AuthorRewardMapper.xml")).exists();
        assertThat(mapping.resolve("GamificationProgressMapper.xml")).exists();
        assertThat(mapping.resolve("ReadingTicketMapper.xml")).exists();
        assertThat(mapping.resolve("ReadingSubscriptionMapper.xml")).exists();
        assertThat(mapping.resolve("ReadingSubscriptionPurchaseMapper.xml")).exists();
        assertThat(mapping.resolve("GiftCodeMapper.xml")).exists();
    }
}
