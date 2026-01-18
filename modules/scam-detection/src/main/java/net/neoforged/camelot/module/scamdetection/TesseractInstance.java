package net.neoforged.camelot.module.scamdetection;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.awt.image.BufferedImage;

public class TesseractInstance {
    private final Tesseract tesseract;

    public TesseractInstance(int psmMode) {
        this.tesseract = new Tesseract();
        tesseract.setPageSegMode(psmMode);
    }

    public String extractText(BufferedImage image) throws TesseractException {
        return tesseract.doOCR(image);
    }
}
