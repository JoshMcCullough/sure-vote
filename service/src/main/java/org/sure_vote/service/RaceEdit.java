package org.sure_vote.service;

import java.util.UUID;

import org.sure_vote.entity.ElectionMethod;

public record RaceEdit(UUID raceId, String name, ElectionMethod method, UUID previousRaceId) {
}
