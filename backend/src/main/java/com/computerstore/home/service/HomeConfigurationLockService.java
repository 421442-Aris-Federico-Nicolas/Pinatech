package com.computerstore.home.service;

import com.computerstore.home.repository.HomeSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HomeConfigurationLockService {
    private final HomeSectionRepository sections;

    public HomeConfigurationLockService(HomeSectionRepository sections) {
        this.sections = sections;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock() {
        sections.lockConfiguration();
    }
}
