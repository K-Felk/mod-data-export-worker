package org.folio.dew.service.validation;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.folio.dew.domain.dto.UserContentUpdateCollection;
import org.folio.dew.error.ContentUpdateValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;

@SpringBootTest
class UserContentUpdateValidatorServiceTest {
  @Autowired
  private UserContentUpdateValidatorService validatorService;
  @ParameterizedTest
  @EnumSource(UserContentUpdateValidTestData.class)
  void shouldAllowValidContentUpdates(UserContentUpdateValidTestData update) {
    var updateCollection = new UserContentUpdateCollection()
      .userContentUpdates(Collections.singletonList(update.getUpdate()))
      .totalRecords(1);

    assertThat(validatorService.validateContentUpdateCollection(updateCollection), is(true));
  }

  @ParameterizedTest
  @EnumSource(UserContentUpdateInvalidTestData.class)
  void shouldRejectInvalidContentUpdates(UserContentUpdateInvalidTestData update) {
    var updateCollection = new UserContentUpdateCollection()
      .userContentUpdates(Collections.singletonList(update.getUpdate()))
      .totalRecords(1);

    assertThrows(ContentUpdateValidationException.class, () -> validatorService.validateContentUpdateCollection(updateCollection));
  }
}
