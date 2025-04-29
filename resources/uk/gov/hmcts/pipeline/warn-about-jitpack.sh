# Check gradle files in project to see if jitpack.io is being referenced
if [[ -n $(grep -r --include="*.gradle" 'jitpack.io' .) ]]; then
    echo "Jitpack use detected"
    exit 1
else
    echo "Project is fine"
fi