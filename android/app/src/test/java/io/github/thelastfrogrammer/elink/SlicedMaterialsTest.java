package io.github.thelastfrogrammer.elink;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class SlicedMaterialsTest {
    private Map<String,String> raw(String types,String colors,String lengths,String masses) {
        Map<String,String> r=new HashMap<>();if(types!=null)r.put(SlicedMaterials.TYPES,types);if(colors!=null)r.put(SlicedMaterials.COLORS,colors);if(lengths!=null)r.put(SlicedMaterials.LENGTHS,lengths);if(masses!=null)r.put(SlicedMaterials.MASSES,masses);return r;
    }
    @Test public void readsVectorPositionsWithZerosAndDoesNotClaimTrayMapping() {
        SlicedMaterials m=SlicedMaterials.from(raw("PLA;PETG","#112233;#ABCDEF","0,2000","0,6.2"),false);assertEquals(2,m.entries.size());assertEquals("PETG",m.entries.get(1).type);assertEquals(Double.valueOf(0),m.entries.get(0).lengthMm);assertEquals(Double.valueOf(6.2),m.entries.get(1).massG);assertTrue(m.warnings.isEmpty());assertTrue(m.text().contains("not confirmed tool or tray IDs"));
    }
    @Test public void mismatchedVectorsFlagUnverifiedAlignmentAndMissingRemainsUnknown() {
        SlicedMaterials m=SlicedMaterials.from(raw("PLA;PETG",null,"1",null),false);assertTrue(m.text().contains("alignment is unverified"));assertNull(m.entries.get(1).lengthMm);assertNull(m.entries.get(0).massG);
    }
    @Test public void invalidUsageDoesNotBecomeZeroOrNonzeroEvidence() {
        SlicedMaterials m=SlicedMaterials.from(raw(null,null,"NaN,-1,Infinity,1e13,abc,1e3",null),false);for(int i=0;i<5;i++)assertNull(m.entries.get(i).lengthMm);assertEquals(Double.valueOf(1000),m.entries.get(5).lengthMm);assertTrue(m.text().contains("not treated as zero"));
    }
    @Test public void interpretsOnlySixDigitHexColorsAndStripsSimpleQuotes() {
        SlicedMaterials m=SlicedMaterials.from(raw("\"PLA\";\"PETG\"","\"#aabbcc\";#12345678",null,null),false);assertEquals("PLA",m.entries.get(0).type);assertEquals("#aabbcc",m.entries.get(0).color);assertNull(m.entries.get(1).color);assertTrue(m.text().contains("#RRGGBB"));
    }
    @Test public void ambiguousQuotedValuesAreOmitted() {
        SlicedMaterials m=SlicedMaterials.from(raw("\"PA;12\"",null,null,null),false);assertTrue(m.entries.isEmpty());assertTrue(m.text().contains("Ambiguous"));
    }
    @Test public void conflictingUsageIndicatorsAreFlagged() {
        SlicedMaterials m=SlicedMaterials.from(raw(null,null,"0,100","1,0"),false);assertTrue(m.text().contains("indicators disagree"));
    }
    @Test public void capsRowsAndRejectsOversizedVectorsRatherThanTruncatingEvidence() {
        SlicedMaterials m=SlicedMaterials.from(raw(null,null,"1,2,3,4,5,6,7,8,9",null),false);assertEquals(8,m.entries.size());assertTrue(m.text().contains("first eight"));
        char[] huge=new char[2049];Arrays.fill(huge,'A');m=SlicedMaterials.from(raw(new String(huge),null,null,null),false);assertTrue(m.entries.isEmpty());assertTrue(m.text().contains("Oversized"));
    }
    @Test public void incompleteSourceAndEmptyValuesRetainUncertainty() {
        SlicedMaterials m=SlicedMaterials.from(raw("PLA;;PETG",null,null,null),true);assertNull(m.entries.get(1).type);assertTrue(m.text().contains("incomplete"));
    }
}
