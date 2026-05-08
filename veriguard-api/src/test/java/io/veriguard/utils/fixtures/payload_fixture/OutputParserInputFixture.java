package io.veriguard.utils.fixtures.payload_fixture;

import io.veriguard.database.model.*;
import io.veriguard.rest.payload.output_parser.OutputParserInput;

public class OutputParserInputFixture {

  public static OutputParserInput createDefaultOutputParseInput() {
    OutputParserInput outputParserInput = new OutputParserInput();
    outputParserInput.setMode(ParserMode.STDOUT);
    outputParserInput.setType(ParserType.REGEX);
    return outputParserInput;
  }
}
