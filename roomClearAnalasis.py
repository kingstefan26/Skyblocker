path = "./run/skyblockerBestMatchClearMobPosition.log"
lines = open(path).read().split('\n')
locations = []
for line in lines:
    if line == "":
        continue
    name, location = line.split(':')
    x, y, z = location.split(',')
    x = int(x)
    y = int(y)
    z = int(z)
    locations.append([name, x, y, z])

# print('Raw data:')
# print(locations)

# organised by room
rooms = {}

for location in locations:
    if location[0] not in rooms:
        rooms[location[0]] = []
    rooms[location[0]].append([location[1],location[2],location[3]])

# print("By room in dict")
# import json
# print(json.dumps(rooms, indent=4, separators=(',', ': ')))

print(f'Loaded {len(locations)} clear mob locatios, from {len(rooms)} rooms')

import math

def dis(a, b):
    return math.sqrt(abs(a[0] - b[0]) + (a[1] - b[1]) + (a[2] - b[2]))

for name, blockPositions in rooms.items():
    if(len(blockPositions) > 1):
        avg_distance = dis(blockPositions[0], blockPositions[1])
        if len(blockPositions) > 2:
            sum_of_distances = 0
            lastVec = blockPositions[0]
            for vec in blockPositions[1:]:
                sum_of_distances += dis(lastVec, vec)
                lastVec = vec
            avg_distance = sum_of_distances / (len(blockPositions) - 1)
        if avg_distance > 3:
            print(f'Seems like {name} is not stable, more then one spawn point? avg dis: {avg_distance} {blockPositions}')