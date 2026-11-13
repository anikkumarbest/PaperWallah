package com.paperwallah.strategy;
import com.paperwallah.model.*;
import java.util.List;
/** Strategy pattern: marks-based and blueprint-based generation share this interface. */
public interface PaperGenerationStrategy {
    List<PaperQuestion> generate(List<Question> bank) throws GenerationException;
    String modeName();
}
