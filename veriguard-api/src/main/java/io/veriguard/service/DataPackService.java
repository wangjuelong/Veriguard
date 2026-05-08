package io.veriguard.service;

import io.veriguard.database.model.DataPack;
import io.veriguard.database.repository.DataPackRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataPackService {
  private final DataPackRepository dataPackRepository;

  public Optional<DataPack> findById(String id) {
    return dataPackRepository.findById(id);
  }

  public DataPack registerDataPack(String id) {
    DataPack dp = new DataPack();
    dp.setId(id);
    return dataPackRepository.save(dp);
  }
}
