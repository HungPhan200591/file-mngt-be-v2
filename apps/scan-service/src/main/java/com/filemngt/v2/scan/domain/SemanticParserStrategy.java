package com.filemngt.v2.scan.domain;

@FunctionalInterface
/** Hợp đồng cho một biến thể parser semantic được registry theo ScanProfile. */
interface SemanticParserStrategy {
    ScanSemanticResult parse(
            ScanProfile profile, String relativePath, String cleanName, SemanticParserSupport.TagClassification tags);
}
