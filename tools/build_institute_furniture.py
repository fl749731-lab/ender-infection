"""Build the institute's distinct equipment and working drawers from editable cuboids.

Uses the repository's existing 16-unit ModelKit/UV exporter. No desktop modeller
or external artwork is needed; these are game models, not concept renders.
"""
from __future__ import annotations

import copy
import json
import sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

from design_catalog import ROOT, PACK, ASSETS, DATA, PROJECT
sys.path.insert(0, str(PACK / 'source'))
from modelkit import Model, PALETTES, rgb
from export_design_assets import geo, obj, texture, write

DEST = ROOT / 'colored_units/institute_furniture_v4'
WALL_BANDS = {
    'cyan': ('#3ba6ad', 'cyan_dye', '青色'),
    'light_blue': ('#72b9d8', 'light_blue_dye', '浅蓝'),
    'green': ('#71a77b', 'lime_dye', '绿色'),
    'yellow': ('#dfb846', 'yellow_dye', '黄色'),
    'purple': ('#9664b5', 'purple_dye', '紫色'),
}
PALETTES.update({
    'ceramic': ['#d9e1e4', '#eaf0ef', '#f5f8f4', '#ffffff'],
    'graphite': ['#1e2930', '#31414c', '#475a66', '#697e88'],
    'teal': ['#267780', '#4ca4ae', '#81c8cf', '#b5e9ec'],
    'blue_case': ['#315e80', '#4d85a9', '#6ea9c5', '#add8e8'],
    'led': ['#42d8eb', '#83f0fb', '#b8faff', '#e3ffff'],
    'status': ['#2f8b49', '#64c86a', '#a2ec98', '#d6ffd0'],
    'fabric': ['#40798e', '#5996aa', '#79b2c1', '#a8d1da'],
    'glass': ['#78a8b9', '#9ec9d6', '#bfdee6', '#e0f7fa'],
})
LABELS = {
    'lab_glass_cabinet': '玻璃样本柜', 'lab_cryo_cabinet': '低温封存柜',
    'lab_server_rack': '服务器机柜', 'lab_control_console': '研究控制台',
    'lab_rolling_cart': '实验室推车', 'lab_microscope': '显微镜',
    'lab_centrifuge': '台式离心机', 'lab_cooling_pump': '冷却泵机组',
    'lab_task_lamp': '实验工作灯', 'lab_privacy_curtain': '医疗隔帘',
    'lab_cabinet': '实验室储柜', 'lab_cabinet_eroded': '侵蚀实验室储柜',
    'lab_workbench': '实验抽屉工作台', 'lab_workbench_eroded': '侵蚀实验抽屉工作台',
    'lab_freezer': '样本抽屉柜', 'lab_storage_crate': '实验室器材箱', 'lab_shelf': '实验室货架',
    'lab_sink': '洗手台储柜',
    'lab_power_generator': '研究所应急发电机', 'lab_distribution_panel': '研究所配电控制器',
    'lab_research_terminal': '研究终端', 'lab_archive_terminal': '核心档案终端',
    'lab_medical_bed': '医疗观察床', 'lab_medical_bed_head': '医疗观察床（床头模型）',
    'lab_waiting_sofa': '研究所等候沙发',
}
LABELS.update({'lab_wall_band_'+color:label+'细色带瓷砖' for color,(_,_,label) in WALL_BANDS.items()})
FIXTURES = {'lab_glass_cabinet', 'lab_cryo_cabinet', 'lab_server_rack', 'lab_control_console',
            'lab_privacy_curtain', 'lab_cabinet', 'lab_cabinet_eroded',
            'lab_workbench', 'lab_workbench_eroded'}
BAKED_STORAGE = {'lab_freezer', 'lab_storage_crate', 'lab_shelf', 'lab_rolling_cart', 'lab_sink'}
CONTROLS = {'lab_power_generator','lab_distribution_panel','lab_research_terminal','lab_archive_terminal'}
MODEL_PARTS = {'lab_medical_bed_head'}
NEW_KEYS = set(LABELS) - {'lab_cabinet', 'lab_cabinet_eroded', 'lab_workbench',
                         'lab_workbench_eroded', 'lab_freezer', 'lab_storage_crate', 'lab_shelf', 'lab_sink'} - MODEL_PARTS


def make(key):
    return Model(key, LABELS[key], 'laboratory', '中央研究所专用设备 / 可储物家具')


def box(m, name, a, b, material='ceramic', bone='root', **kwargs):
    return m.box(name, a, b, material, bone=bone, **kwargs)


def frame(m, height=32):
    box(m, 'base', (-7.5, 0, -7), (7.5, 2, 7), 'graphite')
    box(m, 'back', (-7, 2, 6), (7, height-1, 7))
    box(m, 'side_left', (-7.5, 2, -6), (-6.5, height-1, 6))
    box(m, 'side_right', (6.5, 2, -6), (7.5, height-1, 6))
    box(m, 'cap', (-7.5, height-1, -7), (7.5, height, 7))


def drawer(m, index, x0, x1, y0, y1, z0=-6.7, z1=5.5):
    bone = m.group('drawer_' + str(index))
    box(m, 'drawer_bottom', (x0, y0, z0), (x1, y0+.5, z1), 'steel', bone)
    for x in (x0, x1-.45):
        box(m, 'drawer_side', (x, y0+.5, z0), (x+.45, y1-.4, z1), 'steel', bone)
    box(m, 'drawer_back', (x0, y0+.5, z1-.45), (x1, y1-.4, z1), 'steel', bone)
    box(m, 'drawer_face', (x0-.1, y0-.1, z0-.55), (x1+.1, y1, z0), 'ceramic', bone)
    center = (x0+x1)/2
    box(m, 'drawer_handle', (center-2.1, y1-1.8, z0-1), (center+2.1, y1-1.1, z0-.55), 'graphite', bone)
    box(m, 'drawer_label', (x0+.7, y0+.8, z0-.62), (min(x0+3.1, x1-.3), y0+1.4, z0-.56), 'teal', bone)


def doors(m, glass=False, y0=8, y1=29):
    for sign in (-1, 1):
        bone = m.group('door_' + str(sign), (sign*6.8, 0, -6.8))
        x0, x1 = (-6.8, -.2) if sign < 0 else (.2, 6.8)
        if glass:
            box(m, 'glass_door', (x0+.6, y0+.8, -6.65), (x1-.6, y1-.8, -6.35), 'glass', bone)
            for x in (x0, x1-.65):
                box(m, 'door_vertical', (x, y0, -6.9), (x+.65, y1, -6.25), 'ceramic', bone)
            for y in (y0, y1-.7):
                box(m, 'door_horizontal', (x0, y, -6.9), (x1, y+.7, -6.25), 'ceramic', bone)
        else:
            box(m, 'metal_door', (x0, y0, -6.9), (x1, y1, -6.25), 'ceramic', bone)
            box(m, 'document_slot', (x0+.8, y1-5, -7.05), (x1-.8, y1-2.5, -6.9), 'blue_case', bone)
        handle_x = x1-1.1 if sign < 0 else x0+.6
        box(m, 'door_handle', (handle_x, y0+8, -7.5), (handle_x+.5, y0+12, -6.9), 'steel', bone)


def bottles(m, y, cold=False):
    for x in (-4, 0, 4):
        box(m, 'sample_bottle', (x-.9, y, -1), (x+.9, y+4, 1), 'glass')
        box(m, 'sample_contents', (x-.65, y+.2, -.65), (x+.65, y+2.6, .65), 'blue_case' if cold else 'teal')
        box(m, 'sample_cap', (x-1, y+4, -1), (x+1, y+4.6, 1), 'ceramic')


def cabinet(key, glass=False, cold=False, eroded=False):
    m=make(key); frame(m)
    drawer(m, 0, -6.3, 6.3, 2.2, 7.3)
    for y in (8, 16, 24):
        box(m, 'shelf', (-6.5, y, -5.9), (6.5, y+.55, 6), 'steel')
        if y < 24: bottles(m, y+.6, cold)
    doors(m, glass or cold)
    box(m, 'header', (-6, 29, -7), (6, 31, -6.7), 'teal' if cold else 'blue_case')
    box(m, 'status', (4.2, 29.4, -7.15), (5.2, 30.3, -7), 'status')
    if glass or cold:
        for x in (-6.4, 5.9): box(m, 'inside_light', (x, 8.5, -4.9), (x+.45, 28.5, -4.3), 'led')
    if cold:
        for x,y in ((-3,30),(-4,30),(-2,30),(-3,29.3),(-3,30.7)):
            box(m, 'snow_mark', (x-.2,y-.2,-7.2),(x+.2,y+.2,-7.05),'led')
    if eroded:
        for x in (-5.5,5.5): m.crystal('crystal_growth',(x,24,4),3,6,rot=(0,0,x*3))
    return m


def workbench(key, console=False, eroded=False):
    m=make(key)
    box(m, 'worktop', (-22.5,14,-7.5),(22.5,16,7.5))
    box(m, 'worktop_edge', (-22.5,14,-7.7),(22.5,14.6,-7.5),'teal')
    box(m, 'backsplash', (-22.5,16,6.4),(22.5,20,7.4))
    for side,(x0,x1) in enumerate(((-22,-9),(9,22))):
        box(m,'pedestal_base',(x0,0,-6.8),(x1,2,6),'graphite')
        for x in (x0,x1-.65):box(m,'pedestal_side',(x,2,-6.5),(x+.65,14,6))
        box(m,'pedestal_back',(x0,2,5.5),(x1,14,6))
        for row,y in enumerate((2.2,6.2,10.2)):
            drawer(m,side*3+row,x0+.8,x1-.8,y,y+3.5,z0=-6.8,z1=5.5)
    if console:
        for x in (-15,0,15):
            box(m,'monitor_stand',(x-1,16,2),(x+1,21,4),'steel')
            box(m,'monitor_case',(x-6.2,19.5,.4),(x+6.2,29,2.5),'graphite')
            box(m,'monitor_screen',(x-5.4,20.3,.1),(x+5.4,28,.4),'monitor')
            box(m,'screen_indicator',(x+4.4,19.8,-.05),(x+5.4,20.1,.15),'status')
        box(m,'keyboard',(-10,16,-5.8),(10,16.6,-2),'graphite')
        for x in range(-9,10,2): box(m,'keyboard_keys',(x,16.6,-5.2),(x+1,16.8,-2.7),'steel')
    if eroded:
        for x in (-20,20): m.crystal('bench_crystal',(x,15,4.5),2.5,6,rot=(0,0,x/2))
    return m


def server():
    m=make('lab_server_rack');frame(m)
    for y in (3,7,11,15,19,23,27):
        box(m,'server_blade',(-6, y,-4.7),(6,y+2.8,5),'graphite')
        for x in (-5,-3,-1,1,3):box(m,'vent_slit',(x,y+.5,-4.9),(x+.65,y+2.2,-4.7),'black')
        box(m,'status_led',(4.6,y+1,-5.1),(5.4,y+1.8,-4.9),'status')
    bone=m.group('door_hinge',(6.8,0,-6.7))
    for x in (-6.8,6.1):box(m,'server_door_rail',(x,2,-6.8),(x+.7,30,-6.1),'graphite',bone)
    for y in range(3,30,3):box(m,'mesh_bar',(-6.1,y,-6.7),(6.1,y+.35,-6.3),'steel',bone)
    box(m,'rack_handle',(-6.3,13,-7.3),(-5.7,19,-6.6),'steel',bone)
    return m


def microscope():
    m=make('lab_microscope')
    box(m,'base',(-6,0,-5),(6,1.7,5))
    box(m,'column',(-4.5,1.7,2),(-2.5,11,4),'graphite')
    m.beam('scope_arm',(-3.5,10,3),(1,14,0),2,mat='ceramic')
    box(m,'head',(-1,11,-3),(3,14.5,1))
    box(m,'ocular',(-.2,14,-2),(1.8,16,-.1),'graphite')
    box(m,'objective',(-.3,8.7,-2.5),(1.3,11,-.7),'steel')
    box(m,'stage',(-4.8,6,-3.5),(4.8,7,2),'graphite')
    box(m,'slide',(-1.8,7,-2.4),(1.8,7.2,-.6),'glass')
    box(m,'focus_knob',(-5.4,8,1),(-4.4,10.5,3),'steel')
    box(m,'illuminator',(-1.2,2,-2.8),(1.2,2.6,-.4),'led')
    return m


def centrifuge():
    m=make('lab_centrifuge')
    box(m,'feet',(-6,0,-5),(6,1,5),'graphite')
    box(m,'housing',(-7,1,-6),(7,8,6))
    box(m,'control_strip',(-6,3,-6.3),(6,6,-6),'teal')
    box(m,'display',(-5,4,-6.5),(-1,5.5,-6.3),'monitor')
    for x in (2,4):box(m,'button',(x,4,-6.5),(x+1,5,-6.3),'status')
    box(m,'lid_rim',(-5.8,8,-5),(5.8,9,5),'steel')
    box(m,'lid_window',(-4.5,9,-3.7),(4.5,9.4,3.7),'glass')
    for x,z in ((-3,-2),(0,-2),(3,-2),(-3,2),(0,2),(3,2)):
        box(m,'rotor_tube',(x-.6,7,z-.6),(x+.6,8.6,z+.6),'blue_case')
    return m


def pump():
    m=make('lab_cooling_pump')
    box(m,'skid',(-8,0,-6),(8,2,6),'graphite')
    box(m,'motor_core',(-6,3,-4),(1,11,4),'blue_case')
    for x in (-6,-4,-2,0):box(m,'cooling_fin',(x,2.5,-4.8),(x+.6,11.5,4.8),'steel')
    box(m,'pump_head',(3,3,-5),(7,11,5),'blue_case')
    box(m,'coupling',(1,5,-2),(3,9,2),'warning')
    box(m,'outlet',(4,10,-1.5),(6.5,16,1.5),'steel')
    box(m,'inlet',(4,5,-8),(7,8,-5),'teal')
    box(m,'gauge_stem',(4.4,11,2.5),(5.2,13,3.3),'graphite')
    box(m,'gauge',(3.5,12.5,2.1),(6.1,15,2.7),'ceramic')
    box(m,'gauge_needle',(4.6,13,1.9),(5,14.5,2.1),'warning')
    return m


def lamp():
    m=make('lab_task_lamp')
    box(m,'base',(-6,0,2),(-1,1,7),'steel')
    box(m,'upright',(-4.2,1,4.2),(-3.1,10.5,5.3),'graphite')
    m.beam('arm',(-3.7,10,4.7),(1,13,-1),1,mat='steel')
    box(m,'lamp_case',(-3,12,-6),(5,14,0),'graphite')
    box(m,'lamp_face',(-2.3,11.7,-5.3),(4.3,12, -.7),'led')
    return m


def cart():
    m=make('lab_rolling_cart')
    for x in (-5.5,4):
        for z in (-4,3):box(m,'caster',(x,0,z),(x+1.5,2,z+1.5),'graphite')
    for x in (-6,5):
        for z in (-4.8,3.8):box(m,'cart_post',(x,2,z),(x+1,14,z+1),'steel')
    for y in (3,10):box(m,'cart_tray',(-6,y,-5),(6,y+.8,5))
    for x in (-3.5,.5):
        box(m,'supply_case',(x,3.8,-3.5),(x+3,8.5,3.5),'blue_case')
        box(m,'case_label',(x+.4,5,-3.65),(x+2.5,6.2,-3.5),'ceramic')
    bottles(m,10.8)
    box(m,'push_handle',(-6,14,4),(6,15,5),'steel')
    return m


def curtain():
    m=make('lab_privacy_curtain')
    box(m,'curtain_track',(-8,30,-1),(8,31.5,1),'steel')
    for sign in (-1,1):
        bone=m.group('curtain_left' if sign<0 else 'curtain_right')
        x0,x1=(-7.4,-.1) if sign<0 else (.1,7.4)
        box(m,'cloth',(x0,1,-.35),(x1,29.5,.35),'fabric',bone)
        for x in np.arange(x0+.6,x1,.95):
            box(m,'fabric_fold',(x,1,-.55),(min(x+.18,x1),29,.55),'fabric',bone,variant=2)
        box(m,'cloth_header',(x0,27,-.6),(x1,29.5,.6),'ceramic',bone)
    return m


def freezer():
    m=make('lab_freezer');frame(m,16)
    for row,y in enumerate((2.2,8.5)):drawer(m,row,-6.3,6.3,y,y+5.2)
    return m


def crate():
    m=make('lab_storage_crate')
    box(m,'case_bottom',(-7,0,-7),(7,1,7),'graphite')
    for x in (-7,6):box(m,'case_side',(x,1,-7),(x+1,13,7))
    for z in (-7,6):box(m,'case_end',(-6,1,z),(6,13,z+1))
    for x in (-5.5,4.5):box(m,'case_strap',(x,.5,-7.2),(x+1,13,7.2),'steel')
    box(m,'case_handle',(-2,6,-7.7),(2,7.2,-7.1),'graphite')
    box(m,'case_label',(-4.5,9,-7.25),(-.5,11.5,-7.05),'blue_case')
    bone=m.group('lid',(0,13,6.8))
    box(m,'lid',(-7.5,13,-7.5),(7.5,14.6,7.5),'ceramic',bone)
    for x in (-5.5,4.5):box(m,'lid_strap',(x,14.6,-7.2),(x+1,15,7.2),'steel',bone)
    return m


def shelf():
    m=make('lab_shelf')
    for x in (-7,6):
        for z in (-5,4):box(m,'upright',(x,0,z),(x+1,16,z+1),'steel')
    for y in (1,7,14.5):box(m,'shelf',(-7,y,-5),(7,y+.6,5),'ceramic')
    for i,(x,y) in enumerate(((-5,1.7),(1.5,1.7),(-5,7.7),(1.5,7.7))):
        bone=m.group('drawer_'+str(i))
        box(m,'sample_bin',(x,y,-3.8),(x+4,y+4,3.8),'blue_case',bone)
        box(m,'bin_label',(x+.5,y+1,-4),(x+3.5,y+2.1,-3.8),'ceramic',bone)
    return m


def sink():
    m=make('lab_sink')
    box(m,'cabinet_base',(-7,0,-6),(7,1,6),'graphite')
    for x in (-7,6):box(m,'cabinet_side',(x,1,-6),(x+1,11,6))
    box(m,'cabinet_back',(-6,1,5.2),(6,11,6))
    bone=m.group('door_hinge',(6.5,0,-6))
    box(m,'sink_door',(-6.5,1.2,-6.5),(6.5,10.5,-5.9),'ceramic',bone)
    box(m,'sink_door_handle',(3,5,-7.1),(5,5.8,-6.5),'graphite',bone)
    box(m,'basin_bottom',(-6,10,-5),(6,10.5,5),'steel')
    for a,b in [((-8,11,-7),(-5,13,7)),((5,11,-7),(8,13,7)),((-5,11,-7),(5,13,-4)),((-5,11,4),(5,13,7))]:box(m,'basin_rim',a,b,'ceramic')
    box(m,'basin_water',(-4.8,10.6,-3.8),(4.8,10.8,3.8),'teal')
    box(m,'tap_stem',(-.6,12,4.8),(.6,16,6),'steel')
    box(m,'tap',(-.6,15,1.5),(.6,16,5),'steel')
    return m


def operations_control(key):
    """One-cell operating face; no oversized geometry hidden in adjacent cells."""
    m=make(key)
    box(m,'steel_plinth',(-7,0,-6.5),(7,2,6.5),'graphite')
    if key=='lab_power_generator':
        box(m,'engine_case',(-6.5,2,-5.8),(6.5,12,5.8),'blue_case')
        for x in (-5,-3,-1,1,3):box(m,'engine_vent',(x,4,-6.2),(x+.7,9,-5.8),'graphite')
        box(m,'fuse_access',(3.8,4,-6.4),(5.7,8,-6.15),'warning')
        box(m,'top_cowl',(-7,12,-6),(7,14,6))
        box(m,'meter',(-5,10,-6.4),(-1,12,-6.1),'monitor')
        box(m,'start_button',(2,10,-6.5),(4,12,-6.1),'status')
        box(m,'exhaust',(-5,14,3),(-3,16,5),'steel')
    elif key=='lab_distribution_panel':
        box(m,'switch_box',(-6.8,2,-5.5),(6.8,16,5.5))
        box(m,'recess',(-5.8,5,-5.9),(5.8,14,-5.5),'graphite')
        for x in (-4,-1,2):
            box(m,'breaker',(x,7,-6.5),(x+1.8,11,-5.8),'steel')
            box(m,'breaker_handle',(x+.3,9,-7),(x+1.5,10.3,-6.4),'warning')
        box(m,'status_window',(-5,12,-6.2),(5,13.3,-5.9),'status')
        box(m,'cable_port',(-4,2,-6),(4,4,-5.5),'graphite')
    else:
        box(m,'pedestal',(-5,2,-4),(5,10,5))
        box(m,'terminal_case',(-7,9,-5.8),(7,15.8,3),'graphite')
        box(m,'terminal_screen',(-6.1,10,-6.1),(6.1,15,-5.8),'monitor')
        for y in (11,12,13):box(m,'readout',(-5.2,y,-6.25),(2.5 if y==12 else 4.8,y+.3,-6.1),'led')
        box(m,'control_shelf',(-7,7.5,-7),(7,9,-3),'ceramic')
        for x in range(-5,6,2):box(m,'terminal_key',(x,9,-6),(x+1,9.3,-4.5),'steel')
        box(m,'card_slot',(3,4,-4.3),(6,5,-4),'teal' if key=='lab_research_terminal' else 'warning')
    return m


def medical_bed(head=False):
    m=make('lab_medical_bed_head' if head else 'lab_medical_bed')
    # Both halves stay within their native BedBlock cell, joined along Z.
    for x in (-6.8,5.5):
        for z in (-5.8,4.5):
            box(m,'caster',(x,0,z),(x+1.3,2,z+1.3),'graphite')
            box(m,'leg',(x+.2,1,z),(x+1.1,5,z+1),'steel')
    box(m,'bed_frame',(-7,4,-8),(7,5.5,8),'steel')
    box(m,'mattress',(-6.6,5.5,-8),(6.6,8.5,8),'ceramic')
    box(m,'teal_sheet',(-6.6,8.5,-8),(6.6,8.8,8),'fabric')
    if head:
        box(m,'pillow',(-5.4,8.8,-6.8),(5.4,10,-1.8),'ceramic')
        box(m,'headboard',(-7.5,4,-8),(7.5,14,-7),'ceramic')
        box(m,'headboard_insert',(-5.5,9,-8.1),(5.5,12,-8),'teal')
    else:
        box(m,'footboard',(-7.5,4,7),(7.5,11,8),'ceramic')
        box(m,'patient_card',(-2.5,7,8),(2.5,9,8.1),'blue_case')
    for x in (-7.5,6.9):
        for z in (-5,4):box(m,'rail_post',(x,5,z),(x+.6,12,z+.6),'steel')
        box(m,'safety_rail',(x,11,-5),(x+.6,12,4.6),'steel')
    return m


def waiting_sofa():
    m=make('lab_waiting_sofa')
    for x in (-6,5):
        for z in (-4,4):box(m,'sofa_leg',(x,0,z),(x+1,3,z+1),'steel')
    box(m,'sofa_base',(-8,3,-6),(8,5,7),'graphite')
    box(m,'seat_cushion',(-8,5,-6),(8,8,4.5),'fabric')
    box(m,'back_cushion',(-8,6,4.5),(8,15,7),'fabric')
    box(m,'back_seam',(-.2,8,4.3),(.2,14.5,4.5),'teal')
    box(m,'seat_seam',(-.15,8,-5.5),(.15,8.15,4),'teal')
    return m


def open_data(data):
    out=copy.deepcopy(data)
    def visit(group):
        if group['name'].startswith('drawer_'):
            ids={c for c in group['children'] if isinstance(c,str)}
            for element in out['elements']:
                if element['uuid'] in ids:
                    element['from'][2]-=4; element['to'][2]-=4
        if group['name']=='lid':group['rotation']=[75,0,0]
        if group['name']=='door_hinge':group['rotation']=[0,-90,0]
        for child in group['children']:
            if isinstance(child,dict):visit(child)
    for group in out['outliner']:visit(group)
    return out


def glowmask(path):
    image=Image.open(path).convert('RGBA');pixels=np.array(image)
    selected=np.zeros(pixels.shape[:2],dtype=bool)
    for material in ('led','status','glow','crystal'):
        for color in PALETTES[material]:selected|=np.all(pixels[:,:,:3]==rgb(color),axis=2)
    pixels[~selected]=0
    target=path.with_name(path.stem+'_glowmask.png')
    if (pixels[:,:,3]>0).any():Image.fromarray(pixels).save(target)
    elif target.exists():target.unlink()


def wall_bands():
    destination=ASSETS/'textures/block/design';destination.mkdir(parents=True,exist_ok=True)
    tile=Image.new('RGBA',(16,16),(242,246,242,255))
    for u in range(16):
        tile.putpixel((u,0),(218,227,228,255));tile.putpixel((0,u),(218,227,228,255))
    tile.save(destination/'lab_wall_white.png')
    for color,(accent,dye,_) in WALL_BANDS.items():
        key='lab_wall_band_'+color;image=tile.copy()
        for y in (12,13,14):
            for x in range(16):image.putpixel((x,y),(*rgb(accent),255))
        image.save(destination/(key+'.png'))
        write(ASSETS/'models/block'/f'{key}.json',{'parent':'minecraft:block/cube_column','textures':{'end':'ender_infection:block/design/lab_wall_white','side':f'ender_infection:block/design/{key}'}})
        write(ASSETS/'blockstates'/f'{key}.json',{'variants':{'':{'model':f'ender_infection:block/{key}'}}})
        write(ASSETS/'models/item'/f'{key}.json',{'parent':f'ender_infection:block/{key}'})
        write(DATA/'loot_tables/blocks'/f'{key}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'ender_infection:'+key}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
        write(DATA/'recipes'/f'{key}.json',{'type':'minecraft:crafting_shaped','category':'building','pattern':['WWW','WDW','WWW'],'key':{'W':{'item':'minecraft:white_concrete'},'D':{'item':'minecraft:'+dye}},'result':{'item':'ender_infection:'+key,'count':8}})


def dim_texture(path):
    im=Image.open(path).convert('RGBA');pixels=np.array(im)
    rgb_pixels=pixels[:,:,:3].astype(int)
    mask=(rgb_pixels[:,:,2]>rgb_pixels[:,:,0]*1.15)&(rgb_pixels[:,:,1]>rgb_pixels[:,:,0]*1.10)&(rgb_pixels.max(axis=2)>140)
    for material in ('led','status','monitor'):
        for color in PALETTES.get(material,[]):mask|=np.all(rgb_pixels==rgb(color),axis=2)
    pixels[mask,:3]=[39,58,66]
    Image.fromarray(pixels).save(path)


def managed_panel_variants():
    key='lab_light_panel';folder=ASSETS/'models/block/design';textures=ASSETS/'textures/block/design'
    # Preserve the original lit atlas; repeated source builds remain deterministic.
    powered=textures/f'{key}_powered.png'
    if not powered.exists():Image.open(textures/f'{key}.png').save(powered)
    Image.open(powered).save(textures/f'{key}.png');dim_texture(textures/f'{key}.png')
    for suffix in ('obj','mtl'):
        source=folder/f'{key}.{suffix}'
        (folder/f'{key}_powered.{suffix}').write_text(source.read_text('utf-8').replace(key,key+'_powered'),encoding='utf-8')
    model=json.loads((ASSETS/'models/block'/f'{key}.json').read_text('utf-8'))
    model['model']=f'ender_infection:models/block/design/{key}_powered.obj'
    model['textures']['particle']=f'ender_infection:block/design/{key}_powered'
    write(ASSETS/'models/block'/f'{key}_powered.json',model)
    write(ASSETS/'blockstates'/f'{key}.json',{'variants':{f'facing={face},powered={power}':{'model':f'ender_infection:block/{key}'+('_powered' if power=='true' else ''),'y':angle} for face,angle in [('north',0),('east',90),('south',180),('west',270)] for power in ('false','true')}})


ITEMS={
 'lab_fuse':('实验室保险丝','Laboratory Fuse'),
 'raw_tissue_sample':('原始组织样本','Raw Tissue Sample'),
 'research_record':('研究记录片','Research Record'),
 'separated_reagent':('分离试剂','Separated Reagent'),
 'research_keycard':('研究所门禁卡','Research Keycard'),
 'core_archive':('核心档案','Core Archive'),
 'research_log':('研究日志','Research Log'),
}
PROCESSING_LABELS={
 'input':('样本','Input'), 'reagent':('试剂','Reagent'), 'no_reagent':('无试剂','None'),
 'output':('产物','Output'), 'needs_power':('等待分区供电','Waiting for zone power'),
 'output_full':('产物槽已满','Output slot full'), 'running':('处理中','Processing'),
 'insert_sample':('放入样本','Insert a sample'),
}
OPERATIONS_LABELS={
 'journal':('研究日志 · 设施起点 %s, %s, %s','Research log · Institute origin %s, %s, %s'),
}


def research_items():
    folder=ASSETS/'textures/item';folder.mkdir(parents=True,exist_ok=True)
    for key in ITEMS:
        im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
        if key=='lab_fuse':
            d.rectangle((2,6,13,10),fill='#31414c');d.rectangle((3,7,12,9),fill='#a8e0e4')
            d.rectangle((1,5,4,11),fill='#d6e2e4');d.rectangle((11,5,14,11),fill='#d6e2e4')
            d.line((5,8,7,7,9,9,10,8),fill='#efbc53',width=1)
        elif key in ('raw_tissue_sample','separated_reagent'):
            d.rectangle((5,2,10,4),fill='#d9e1e4');d.rectangle((4,4,11,13),fill='#91becd')
            d.rectangle((5,6,10,12),fill='#b564a4' if key=='raw_tissue_sample' else '#51ccba')
            d.line((5,5,5,11),fill='#def8f7');d.rectangle((4,13,11,14),fill='#455a68')
        elif key=='research_keycard':
            d.rectangle((1,4,14,12),fill='#e9efec');d.rectangle((2,5,13,6),fill='#488e9b')
            d.rectangle((3,8,6,10),fill='#ddbf69');d.line((8,8,12,8),fill='#465762');d.line((8,10,11,10),fill='#465762')
        elif key=='core_archive':
            d.rectangle((3,2,12,14),fill='#384652');d.rectangle((5,4,10,10),fill='#ab70cb')
            d.rectangle((5,12,10,13),fill='#e4e8e0');d.rectangle((7,5,8,9),fill='#efd4ff')
        else:
            d.rectangle((3,1,12,14),fill='#304e60' if key=='research_log' else '#d6dedc')
            d.rectangle((5,2,12,13),fill='#e5e9dc');d.line((4,2,4,13),fill='#4b8c9a')
            for y in (4,6,8,10):d.line((6,y,10 if y==8 else 11,y),fill='#758b8d')
        im.save(folder/f'{key}.png')
        write(ASSETS/'models/item'/f'{key}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'ender_infection:item/{key}'}})
    recipes={
      'lab_fuse':([' I ','IRI',' I '],{'I':'minecraft:iron_nugget','R':'minecraft:redstone'},2),
      'research_keycard':(['IPI',' R '],{'I':'minecraft:iron_nugget','P':'minecraft:paper','R':'ender_infection:research_record'},1),
      'research_log':([' P ','RBR',' P '],{'P':'minecraft:paper','R':'ender_infection:research_record','B':'minecraft:book'},1),
    }
    for key,(pattern,ingredients,count) in recipes.items():
        write(DATA/'recipes'/f'{key}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':{letter:{'item':item} for letter,item in ingredients.items()},'result':{'item':'ender_infection:'+key,'count':count}})


def export():
    models=[cabinet('lab_cabinet'),cabinet('lab_cabinet_eroded',eroded=True),
            cabinet('lab_glass_cabinet',glass=True),cabinet('lab_cryo_cabinet',cold=True),
            workbench('lab_workbench'),workbench('lab_workbench_eroded',eroded=True),
            workbench('lab_control_console',console=True),server(),microscope(),centrifuge(),
            pump(),lamp(),cart(),curtain(),freezer(),crate(),shelf(),sink(),
            *[operations_control(key) for key in sorted(CONTROLS)],medical_bed(),medical_bed(True),waiting_sofa()]
    report=[]
    for model in models:
        data,_,metadata=model.save(DEST);key=model.key
        metadata['file']=Path(metadata['file']).relative_to(DEST).as_posix();report.append(metadata)
        if key in FIXTURES:
            geo(key,data,fixture=True)
            texture(data,ASSETS/'textures/block/design'/f'{key}.png')
            glowmask(ASSETS/'textures/block/design'/f'{key}.png')
            write(ASSETS/'animations'/f'{key}.animation.json',{'format_version':'1.8.0','animations':{}})
            write(ASSETS/'models/block'/f'{key}.json',{'parent':'minecraft:block/block','textures':{'particle':f'ender_infection:block/design/{key}'},'elements':[]})
        else:
            baked=obj(key,data,'block',normalize=False)
            if key=='lab_rolling_cart':baked['render_type']='minecraft:translucent'
            write(ASSETS/'models/block'/f'{key}.json',baked)
        if key in MODEL_PARTS:continue
        variants={}
        for face,angle in [('north',0),('east',90),('south',180),('west',270)]:
            if key in BAKED_STORAGE:
                for value in ('false','true'):variants[f'facing={face},open={value}']={'model':f'ender_infection:block/{key}'+('_open' if value=='true' else ''),'y':angle}
            elif key in CONTROLS or key=='lab_task_lamp':
                for power in ('false','true'):variants[f'facing={face},powered={power}']={'model':f'ender_infection:block/{key}'+('_powered' if power=='true' else ''),'y':angle}
            elif key=='lab_medical_bed':
                for part in ('foot','head'):
                    for occupied in ('false','true'):variants[f'facing={face},part={part},occupied={occupied}']={'model':f'ender_infection:block/{key}'+('_head' if part=='head' else ''),'y':angle}
            else:variants[f'facing={face}']={'model':f'ender_infection:block/{key}','y':angle}
        if key in CONTROLS or key=='lab_task_lamp':
            write(ASSETS/'models/block'/f'{key}_powered.json',obj(key+'_powered',data,'block',normalize=False))
            dim_texture(ASSETS/'textures/block/design'/f'{key}.png')
        if key in BAKED_STORAGE:
            baked=obj(key+'_open',open_data(data),'block',normalize=False)
            if key=='lab_rolling_cart':baked['render_type']='minecraft:translucent'
            write(ASSETS/'models/block'/f'{key}_open.json',baked)
        write(ASSETS/'blockstates'/f'{key}.json',{'variants':variants})
        obj(key,data,'item')
        if key in NEW_KEYS:
            conditions=[{'condition':'minecraft:survives_explosion'}]
            if key=='lab_medical_bed':conditions.append({'condition':'minecraft:block_state_property','block':'ender_infection:lab_medical_bed','properties':{'part':'head'}})
            write(DATA/'loot_tables/blocks'/f'{key}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'ender_infection:'+key}],'conditions':conditions}]})
            ingredient='minecraft:glass' if 'cabinet' in key else 'minecraft:redstone' if key in CONTROLS|{'lab_server_rack','lab_control_console'} else 'minecraft:blue_wool' if key in ('lab_privacy_curtain','lab_medical_bed','lab_waiting_sofa') else 'minecraft:glass_bottle'
            recipe={'type':'minecraft:crafting_shaped','category':'building','pattern':['IGI','IRI'],'key':{'I':{'item':'minecraft:iron_ingot'},'G':{'item':ingredient},'R':{'item':'minecraft:redstone'}},'result':{'item':'ender_infection:'+key}}
            if key=='lab_medical_bed':recipe.update(pattern=['WWW','III'],key={'W':{'item':'minecraft:cyan_wool'},'I':{'item':'minecraft:iron_ingot'}})
            if key=='lab_waiting_sofa':recipe.update(pattern=[' W ','WWW','III'],key={'W':{'item':'minecraft:cyan_wool'},'I':{'item':'minecraft:iron_nugget'}})
            write(DATA/'recipes'/f'{key}.json',recipe)
    wall_bands()
    managed_panel_variants()
    research_items()
    for lang in ('zh_cn','en_us'):
        path=ASSETS/'lang'/f'{lang}.json';content=json.loads(path.read_text('utf-8'))
        content.update({'block.ender_infection.'+key:label if lang=='zh_cn' else key.replace('_',' ').title() for key,label in LABELS.items() if key not in MODEL_PARTS})
        content.update({'item.ender_infection.'+key:labels[0 if lang=='zh_cn' else 1] for key,labels in ITEMS.items()})
        content.update({'gui.ender_infection.processing.'+key:labels[0 if lang=='zh_cn' else 1] for key,labels in PROCESSING_LABELS.items()})
        content.update({'message.ender_infection.operations.'+key:labels[0 if lang=='zh_cn' else 1] for key,labels in OPERATIONS_LABELS.items()})
        write(path,content)
    tag=PROJECT/'src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json'
    content=json.loads(tag.read_text('utf-8'));content['values']=sorted(set(content['values'])|{'ender_infection:'+k for k in NEW_KEYS});write(tag,content)
    bed_tag=PROJECT/'src/main/resources/data/minecraft/tags/blocks/beds.json'
    content=json.loads(bed_tag.read_text('utf-8')) if bed_tag.exists() else {'replace':False,'values':[]}
    content['values']=sorted(set(content['values'])|{'ender_infection:lab_medical_bed'});write(bed_tag,content)
    write(DEST/'manifest.json',{'models':report,'count':len(report),'wall_bands':list(WALL_BANDS),'units_per_block':16,'source':'tools/build_institute_furniture.py','opening':'Named drawer/door bones for Gecko fixtures; OPEN baked variants for one-block storage.'})
    print(json.dumps({'models':len(report),'new_block_types':len(NEW_KEYS),'source':DEST.relative_to(ROOT).as_posix()}))


if __name__=='__main__':export()
