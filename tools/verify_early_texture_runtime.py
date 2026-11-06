"""Use the same strict runtime gates for early stages, material polish and spitter."""
import verify_quadruped_runtime as review

review.SUBJECTS=('cow','pig','sheep','cow_early','pig_early','sheep_early','spider_early','throat_spitter')
review.DEST=review.ROOT/'previews/early-texture-rework-v2'
review.CHECKS=review.ROOT/'build/early-texture-rework-checks'
review.EXTRA_SOURCES=tuple(review.ROOT/p for p in [
 'colored_units/design_pack_2026-11-02_v2/monsters/rift_throat_spitter.bbmodel',
 'colored_units/design_pack_2026-11-02_v2/manifest.json',
 'tools/verify_early_texture_runtime.py',
 'tools/pack_quadruped_materials.ps1',
])

if __name__=='__main__':review.main()
