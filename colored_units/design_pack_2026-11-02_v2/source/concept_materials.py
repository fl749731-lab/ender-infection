"""Neutral, readable bone armor for the concept-matched creature revisions."""
from contextlib import contextmanager
from modelkit import PALETTES

# Crystals retain their established palette and full emissive coverage.
CONCEPT_PALETTES = {
    'bone': ['#555159', '#827d82', '#aaa3a2', '#d0c8bf'],
    'skull': ['#6c665f', '#a49a8d', '#c9bfb0', '#e3d7c4'],
    'shell': ['#272432', '#3b3545', '#514957', '#6c626f'],
    'tissue': ['#24172e', '#3f254c', '#644070', '#8b5b97'],
    'membrane': ['#2c1937', '#4a2c57', '#70437f', '#9b60aa'],
    'spore': ['#735880', '#9c7cad', '#bc9bca', '#dec3e6'],
    'brood': ['#482d55', '#704b7c', '#986aaa', '#c299d1'],
}


@contextmanager
def concept_palette():
    original = {key: PALETTES.get(key) for key in CONCEPT_PALETTES}
    PALETTES.update(CONCEPT_PALETTES)
    try:
        yield
    finally:
        for key, value in original.items():
            if value is None:
                PALETTES.pop(key, None)
            else:
                PALETTES[key] = value
