package ai.chat2db.community.domain.core.impl.ai;

import java.util.ArrayList;
import java.util.List;

import ai.chat2db.community.domain.api.model.result.ExecuteResponse;
import ai.chat2db.community.domain.api.model.result.ResultCell;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the caveats reach the tool output at all.
 *
 * <p>{@link AiResultCaveats} being right is worth nothing if nothing calls it,
 * and the call sits in the middle of a method that needs a database, a model
 * and a live connection to reach any other way. So the seam is tested here and
 * the judgement is tested next door.
 */
class AiToolServiceImplCaveatsTest {

    @Test
    void aCappedResultGetsACaveatBlockAfterTheRows() {
        StringBuilder output = new StringBuilder("## Result 1\nrows: 200, hasNextPage: true\n\n");

        new AiToolServiceImpl().appendCaveats(
                output, List.of(page(200, true)), "SELECT CompanyName FROM TurnoverRanks", "شرکت‌ها");

        String rendered = output.toString();
        assertTrue(rendered.contains("## Caveats"), rendered);
        assertTrue(rendered.contains("PARTIAL RESULT"), rendered);
        // After the rows, because the end of a tool result is what the answer
        // is written from.
        assertTrue(rendered.indexOf("## Caveats") > rendered.indexOf("rows: 200"), rendered);
    }

    @Test
    void aCompleteResultGetsNoBlockAtAll() {
        StringBuilder output = new StringBuilder("## Result 1\nrows: 12\n\n");
        String before = output.toString();

        new AiToolServiceImpl().appendCaveats(
                output, List.of(page(12, false)), "SELECT CompanyName FROM TurnoverRanks", "شرکت‌ها");

        assertEquals(before, output.toString());
    }

    @Test
    void aResultListWithNothingUsableInItIsLeftAlone() {
        StringBuilder output = new StringBuilder("## Result 1\n");
        String before = output.toString();

        new AiToolServiceImpl().appendCaveats(output, new ArrayList<>(), "SELECT 1", "چیزی");
        assertEquals(before, output.toString());

        List<ExecuteResponse> nulls = new ArrayList<>();
        nulls.add(null);
        new AiToolServiceImpl().appendCaveats(output, nulls, "SELECT 1", "چیزی");
        assertEquals(before, output.toString());
    }

    /** One result carrying {@code rows} rows, and whether more follow it. */
    private static ExecuteResponse page(int rows, boolean hasNextPage) {
        List<List<ResultCell>> data = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            data.add(List.of(new ResultCell()));
        }
        ExecuteResponse response = new ExecuteResponse();
        response.setSuccess(Boolean.TRUE);
        response.setDataList(data);
        response.setHasNextPage(hasNextPage);
        return response;
    }
}
