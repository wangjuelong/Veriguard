package io.veriguard.datapack.local_fixtures;

import static io.veriguard.utils.StringUtils.generateRandomColor;

import io.veriguard.datapack.DataPack;
import io.veriguard.rest.tag.TagService;
import io.veriguard.rest.tag.form.TagCreateInput;
import io.veriguard.service.DataPackService;
import org.springframework.stereotype.Component;

@Component
public class TestDataPack extends DataPack {
  public final String tagName = "test_datapack_tag_name";

  private final TagService tagService;

  public TestDataPack(DataPackService dataPackService, TagService tagService) {
    super(dataPackService);
    this.tagService = tagService;
  }

  @Override
  protected void doProcess() {
    // insert a new tag with static name
    TagCreateInput input = new TagCreateInput();
    input.setName(tagName);
    input.setColor(generateRandomColor());
    tagService.upsertTag(input);
  }
}
