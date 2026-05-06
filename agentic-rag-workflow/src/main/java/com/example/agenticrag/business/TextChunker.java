package com.example.agenticrag.business;

import com.example.agenticrag.model.SourceDocument;
import com.example.agenticrag.model.TextChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Splits large documents into smaller chunks that can be retrieved.
 *
 * <p>Chunking is one of the most important RAG design choices. Chunks that are
 * too large add noise. Chunks that are too small lose context. This first
 * implementation uses character windows with overlap because the behavior is
 * easy to understand and test.</p>
 */
public final class TextChunker {

    private final int maxCharacters;
    private final int overlapCharacters;

    /**
     * Creates a chunker that slices documents into overlapping character windows.
     *
     * <p>The two constructor parameters control the main tradeoff of this PoC:
     * chunk size versus continuity. A larger chunk preserves more context in a
     * single retrieval result but also adds more irrelevant text. A smaller
     * chunk is more precise but risks losing the surrounding context required
     * to answer a question. The overlap keeps some continuity between adjacent
     * chunks so information that sits on a boundary is not lost completely.</p>
     *
     * @param maxCharacters the preferred maximum size of each chunk before we
     *                      look for a cleaner boundary such as a paragraph,
     *                      line, sentence, or space
     * @param overlapCharacters how many characters from the end of the current
     *                          chunk should be reconsidered when starting the
     *                          next one
     */
    public TextChunker(int maxCharacters, int overlapCharacters) {
        if (maxCharacters <= 0) {
            throw new IllegalArgumentException("maxCharacters must be greater than zero");
        }
        if (overlapCharacters < 0) {
            throw new IllegalArgumentException("overlapCharacters must be greater than or equal to zero");
        }
        if (overlapCharacters >= maxCharacters) {
            throw new IllegalArgumentException("overlapCharacters must be smaller than maxCharacters");
        }
        this.maxCharacters = maxCharacters;
        this.overlapCharacters = overlapCharacters;
    }

    /**
     * Splits one source document into ordered chunks that preserve traceability.
     *
     * <p>This method is the core of the chunking strategy. It walks through the
     * document from left to right and repeatedly performs the same sequence:
     * choose a target window, try to move the end of that window to a cleaner
     * semantic boundary, trim surrounding whitespace, create a {@link TextChunk},
     * and finally advance with overlap so the next chunk still sees part of the
     * previous context.</p>
     *
     * <p>The method also keeps the original character offsets. That detail is
     * important in a learning-oriented RAG implementation because it shows that
     * a chunk is not just text content: it is a traceable view over a source
     * document. Those offsets make debugging, citation generation, and later
     * UI highlighting easier.</p>
     *
     * @param document the trusted source document to split into retrieval units
     * @return an immutable list of chunks in source order
     */
    public List<TextChunk> split(SourceDocument document) {
        Objects.requireNonNull(document, "document");
        String content = document.content();
        if (content.isBlank()) {
            return List.of();
        }

        List<TextChunk> chunks = new ArrayList<>();
        int start = skipWhitespaceForward(content, 0, content.length());
        int chunkIndex = 0;

        while (start < content.length()) {
            // Start with a fixed-size window, then try to end it at a more natural boundary.
            int targetEnd = Math.min(start + maxCharacters, content.length());
            int end = chooseBoundary(content, start, targetEnd);
            if (end <= start) {
                end = targetEnd;
            }

            // Trim edge whitespace so stored chunks contain only meaningful content.
            int trimmedStart = skipWhitespaceForward(content, start, end);
            int trimmedEnd = skipWhitespaceBackward(content, trimmedStart, end);
            if (trimmedStart < trimmedEnd) {
                chunks.add(new TextChunk(
                        document.id(),
                        document.title(),
                        document.sourcePath(),
                        chunkIndex++,
                        trimmedStart,
                        trimmedEnd,
                        content.substring(trimmedStart, trimmedEnd)
                ));
            }

            if (end >= content.length()) {
                break;
            }

            // Rewind slightly with overlap, then move to the next word boundary.
            int nextStart = moveToNextWordBoundary(content, Math.max(end - overlapCharacters, start + 1), end);
            start = skipWhitespaceForward(content, nextStart, content.length());
        }

        return List.copyOf(chunks);
    }

    /**
     * Chooses the most natural chunk end close to the requested target window.
     *
     * <p>Pure fixed-width chunking is easy to implement but often produces ugly
     * fragments that cut paragraphs, lines, or sentences in half. This method
     * improves readability and retrieval quality by searching backward from the
     * target end for a better stopping point. The search prefers larger semantic
     * boundaries first: paragraph break, line break, sentence ending, and only
     * then a plain space.</p>
     *
     * <p>The method also enforces a minimum useful boundary. Without that guard,
     * a chunk could collapse into a tiny fragment just because a separator was
     * found too close to the start.</p>
     *
     * @param content the full source text being chunked
     * @param start the inclusive start of the current chunk window
     * @param targetEnd the preferred exclusive end before boundary adjustment
     * @return the chosen exclusive end position for the chunk
     */
    private int chooseBoundary(String content, int start, int targetEnd) {
        if (targetEnd >= content.length()) {
            return content.length();
        }

        int minimumUsefulBoundary = start + Math.max(1, maxCharacters / 2);

        int paragraph = content.lastIndexOf("\n\n", targetEnd);
        if (paragraph >= minimumUsefulBoundary) {
            return paragraph + 2;
        }

        int line = content.lastIndexOf('\n', targetEnd);
        if (line >= minimumUsefulBoundary) {
            return line + 1;
        }

        for (String delimiter : List.of(". ", "! ", "? ")) {
            int sentence = content.lastIndexOf(delimiter, targetEnd);
            if (sentence >= minimumUsefulBoundary) {
                return sentence + delimiter.length();
            }
        }

        int space = content.lastIndexOf(' ', targetEnd);
        if (space >= minimumUsefulBoundary) {
            return space;
        }

        return targetEnd;
    }

    /**
     * Advances an index until the first non-whitespace character is found.
     *
     * <p>We use this helper when starting a chunk so leading blanks are not
     * stored as chunk content, and again when advancing to the next chunk so the
     * next retrieval unit begins with meaningful text.</p>
     *
     * @param content the source text
     * @param start the position from which to start scanning
     * @param limit the exclusive upper bound for the scan
     * @return the first index at or after {@code start} that is not whitespace,
     *         or {@code limit} if none exists
     */
    private int skipWhitespaceForward(String content, int start, int limit) {
        int index = start;
        while (index < limit && Character.isWhitespace(content.charAt(index))) {
            index++;
        }
        return index;
    }

    /**
     * Moves backward from the end of a candidate chunk to remove trailing whitespace.
     *
     * <p>This keeps the stored chunk text clean and stable. Without this step,
     * trailing spaces or line breaks would become part of the chunk payload and
     * make previews, prompts, and later formatting less predictable.</p>
     *
     * @param content the source text
     * @param start the inclusive lower bound for the backward scan
     * @param limit the exclusive upper bound, usually the candidate chunk end
     * @return the exclusive end index after trimming trailing whitespace
     */
    private int skipWhitespaceBackward(String content, int start, int limit) {
        int index = limit;
        while (index > start && Character.isWhitespace(content.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    /**
     * Pushes a start index forward until the current word has finished.
     *
     * <p>After applying overlap, the next chunk could start in the middle of a
     * word. That makes the resulting text noisy and harder to reason about in
     * prompts or citations. This helper moves to the next whitespace boundary so
     * the following chunk starts at a cleaner lexical position.</p>
     *
     * @param content the source text
     * @param start the index produced after overlap is applied
     * @param limit the furthest position we are willing to inspect while fixing
     *              the boundary
     * @return an index positioned at the end of the partial word, ready for a
     *         subsequent whitespace trim
     */
    private int moveToNextWordBoundary(String content, int start, int limit) {
        int index = start;
        while (index < limit && !Character.isWhitespace(content.charAt(index))) {
            index++;
        }
        return index;
    }
}
