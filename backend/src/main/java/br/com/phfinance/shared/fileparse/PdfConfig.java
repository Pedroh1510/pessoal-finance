package br.com.phfinance.shared.fileparse;

import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PdfConfig {

    @Bean
    public Tesseract tesseract(@Value("${tesseract.datapath:}") String datapath) {
        Tesseract t = new Tesseract();
        if (!datapath.isBlank()) t.setDatapath(datapath);
        t.setLanguage("por");
        return t;
    }
}
