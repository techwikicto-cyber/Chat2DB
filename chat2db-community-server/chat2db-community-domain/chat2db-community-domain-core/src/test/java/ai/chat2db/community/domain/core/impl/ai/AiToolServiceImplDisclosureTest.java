package ai.chat2db.community.domain.core.impl.ai;

import java.lang.reflect.Method;
import java.util.List;

import ai.chat2db.community.domain.api.enums.ai.AiDisclosurePolicyEnum;
import ai.chat2db.community.domain.api.model.result.ExecuteResponse;
import ai.chat2db.community.domain.api.model.result.Header;
import ai.chat2db.community.domain.api.model.result.ResultCell;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a result looks like by the time it is handed to a model provider.
 *
 * <p>The one thing worth asserting hard: under {@code NONE}, the values are
 * not in the text. Not truncated, not masked - absent. A test that only
 * checked for the word "policy" in the output would pass while every salary in
 * the result sat two lines below it.
 */
class AiToolServiceImplDisclosureTest {

    private static final String SENSITIVE = "Bijan Motamed";

    @Test
    void underNoneTheValuesAreNotInTheTextAtAll() throws Exception {
        String rendered = render(AiDisclosurePolicyEnum.NONE);

        assertFalse(rendered.contains(SENSITIVE), rendered);
        assertFalse(rendered.contains("8400000"), rendered);
        // The shape survives, because it is what lets the assistant say
        // something true about the query it just ran.
        assertTrue(rendered.contains("rows: 2"), rendered);
        assertTrue(rendered.contains("columns: name, salary"), rendered);
        assertTrue(rendered.contains("disclosure policy is NONE"), rendered);
    }

    @Test
    void underSampleTheRowsGoThroughAsTheyAlwaysDid() throws Exception {
        String rendered = render(AiDisclosurePolicyEnum.SAMPLE);

        assertTrue(rendered.contains(SENSITIVE), rendered);
        assertTrue(rendered.contains("rows: 2"), rendered);
    }

    @Test
    void underFullTheRowsGoThroughToo() throws Exception {
        assertTrue(render(AiDisclosurePolicyEnum.FULL).contains(SENSITIVE));
    }

    /** The tool's own rendering of one result, under one policy. */
    private static String render(AiDisclosurePolicyEnum policy) throws Exception {
        Method format = AiToolServiceImpl.class.getDeclaredMethod(
                "formatExecuteResponse", ExecuteResponse.class, AiDisclosurePolicyEnum.class);
        format.setAccessible(true);
        return (String) format.invoke(new AiToolServiceImpl(), payroll(), policy);
    }

    private static ExecuteResponse payroll() {
        ExecuteResponse response = new ExecuteResponse();
        response.setSuccess(Boolean.TRUE);
        response.setHeaderList(List.of(header("name"), header("salary")));
        response.setDataList(List.of(
                List.of(cell(SENSITIVE), cell("8400000")),
                List.of(cell("Sara Ahmadi"), cell("9100000"))));
        return response;
    }

    private static Header header(String name) {
        Header header = new Header();
        header.setName(name);
        return header;
    }

    private static ResultCell cell(String value) {
        ResultCell cell = new ResultCell();
        cell.setValue(value);
        return cell;
    }
}
