package org.plantuml.idea.rendering;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import org.apache.commons.lang.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.plantuml.idea.rendering.PlantUmlRenderer.getTitles;
import static org.plantuml.idea.rendering.PlantUmlRenderer.zoomDiagram;

public class PlantUmlPartialRenderer extends PlantUmlNormalRenderer {
    private static final Logger logger = Logger.getInstance(PlantUmlPartialRenderer.class);


    @NotNull
    public RenderResult partialRender(RenderRequest renderRequest, @Nullable RenderCacheItem cachedItem, long start, String[] sourceSplit) {
        FileFormatOption formatOption = new FileFormatOption(renderRequest.getFormat().getFormat());

        RenderResult renderResult = new RenderResult(RenderingType.PARTIAL, sourceSplit.length);
            for (int page = 0; page < sourceSplit.length; page++) {
                processPage(renderRequest, cachedItem, sourceSplit[page], formatOption, renderResult, page);
            }

        logger.debug("partial rendering done ", System.currentTimeMillis() - start, "ms");
        return renderResult;
    }

    public void processPage(RenderRequest renderRequest, @Nullable RenderCacheItem cachedItem, String s, FileFormatOption formatOption, RenderResult renderResult, int page) {
        long partialPageProcessingStart = System.currentTimeMillis();
        String partialSource = "@startuml\n" + s + "\n@enduml";

        boolean obsolete = cachedItem == null
                || renderRequest.requestedRefreshOrIncludesChanged()
                || RenderingType.PARTIAL.renderingTypeChanged(cachedItem)
                || renderRequest.getZoom() != cachedItem.getZoom()
                || !partialSource.equals(cachedItem.getImagesItemPageSource(page));

        boolean pageSelected = renderRequest.getPage() == -1 || renderRequest.getPage() == page;
        boolean shouldRender = pageSelected && (obsolete || !cachedItem.hasImage(page));

        if (shouldRender) {
            renderResult.addRenderedImage(renderImage(renderRequest, page, formatOption, partialSource));
        } else if (obsolete) {
            renderResult.addUpdatedTitle(updateTitle(renderRequest, page, partialSource));
        } else {
            logger.debug("page ", page, " cached");
            renderResult.addCachedImage(cachedItem.getImageItem(page));
        }
        logger.debug("processing of page ", page, " done in ", System.currentTimeMillis() - partialPageProcessingStart, "ms");
    }

    private ImageItem updateTitle(RenderRequest renderRequest, int page, String partialSource) {
        long start = System.currentTimeMillis();
        logger.debug("updating title, page ", page);

        SourceStringReader reader = new SourceStringReader(partialSource);
        String title = getTitle(reader);
        ImageItem imageItem = new ImageItem(renderRequest.getBaseDir(), renderRequest.getSource(), partialSource, page, TITLE_ONLY, null, null, RenderingType.PARTIAL, title);

        logger.debug("updateTitle " + (System.currentTimeMillis() - start));

        return imageItem;
    }

    private String getTitle(SourceStringReader reader) {
        Titles titles = getTitles(1, reader.getBlocks());
        if (titles.size() > 1) {
            throw new NotImplementedException("partial rendering not supported with @newpage in included file, and it won't be");
        }
        return titles.get(0);
    }


    private ImageItem renderImage(RenderRequest renderRequest, int page, FileFormatOption formatOption, String partialSource) {
        logger.debug("rendering partially, page ", page);
        SourceStringReader reader = new SourceStringReader(partialSource);
        Pair<Integer, Titles> pages = zoomDiagram(reader, renderRequest.getZoom());
        Integer totalPages = pages.first;
        Titles titles = pages.second;

        if (totalPages > 1) {
            throw new NotImplementedException("partial rendering not supported with @newpage in included file, and it won't be");
        }
        if (titles.size() > 1) {
            logger.warn("too many titles " + titles + ", partialSource=" + partialSource);
        }
        try {
            return new ImageItem(page, generateImageItem(renderRequest, renderRequest.getSource(), partialSource, reader, formatOption, 0, page, RenderingType.PARTIAL, titles.get(0)));
        } catch (RenderingCancelledException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }


}
